#!/usr/bin/env bash
# Production deploy of CV Fit AI on the VPS.
#
#   deploy.sh [<commit sha>]  < resume-tailor.jar
#
# Run by GitHub Actions over SSH after the build passes on main (see .github/workflows/deploy.yml).
# The jar is built on the runner and streamed in on stdin, so the server needs neither Maven nor a
# clone of the repository. The deploy key in authorized_keys is restricted to this script (forced
# command), so the commit it was built from arrives in SSH_ORIGINAL_COMMAND and is only used to
# name the release.
#
# Steps: store the jar under releases/, point current.jar at it, restart the systemd service and wait
# until /login answers. If the new version is not healthy, current.jar is put back on the previous
# release, the service is restarted and the script fails. Secrets live in the service's
# EnvironmentFile and are never touched.
#
# Overridable for tests: APP_DIR, SERVICE, LOG_FILE, HEALTH_URL, HEALTH_RETRIES, HEALTH_INTERVAL, KEEP_RELEASES.
set -euo pipefail

main() {
  local app_dir="${APP_DIR:-/opt/cvfitai}"
  local service="${SERVICE:-cvfitai}"
  local log_file="${LOG_FILE:-/var/log/cvfitai-deploy.log}"
  local health_url="${HEALTH_URL:-http://127.0.0.1:8081/login}"
  local retries="${HEALTH_RETRIES:-40}" interval="${HEALTH_INTERVAL:-3}" keep="${KEEP_RELEASES:-5}"

  local requested="${1:-${SSH_ORIGINAL_COMMAND:-}}"
  requested="${requested//[[:space:]]/}"
  if [[ -n "$requested" && ! "$requested" =~ ^[0-9a-f]{40}$ ]]; then
    echo "deploy: expected a full commit sha, got '${1:-${SSH_ORIGINAL_COMMAND:-}}'" >&2
    return 2
  fi

  mkdir -p "$app_dir/releases"
  exec > >(tee -a "$log_file") 2>&1
  cd "$app_dir"

  # The upload must finish before the lock: it can take a while and must not be lost if a second deploy waits.
  local incoming
  incoming="$(mktemp "$app_dir/releases/.incoming.XXXXXX")"
  trap "rm -f '$incoming'" EXIT
  cat > "$incoming"
  if [[ "$(head -c 2 "$incoming")" != "PK" ]]; then
    echo "deploy: stdin is not a jar (zip) file" >&2
    return 3
  fi

  # One deploy at a time (two pushes in a row).
  if command -v flock >/dev/null; then
    exec 9>"$app_dir/.deploy.lock"
    flock 9
  fi

  local name release previous=""
  name="${requested:-$(date -u +%Y%m%dT%H%M%SZ)}"
  release="$app_dir/releases/cvfitai-$name.jar"
  [[ -e "$app_dir/current.jar" ]] && previous="$(readlink -f "$app_dir/current.jar")"

  echo "=== deploy $(date -u +%FT%TZ) release=$name"
  mv -f "$incoming" "$release"
  chmod 640 "$release"
  chgrp cvfitai "$release" 2>/dev/null || true
  ln -sfn "$release" "$app_dir/current.jar"
  echo "deploy: ${previous:-<none>} -> $release"

  if systemctl restart "$service" && healthy "$health_url" "$retries" "$interval"; then
    prune "$app_dir/releases" "$keep" "$release" "$previous"
    echo "deploy: OK $name"
    return 0
  fi

  if [[ -n "$previous" && -e "$previous" ]]; then
    echo "deploy: new version is unhealthy, rolling back to $previous"
    ln -sfn "$previous" "$app_dir/current.jar"
    systemctl restart "$service" || true
  else
    echo "deploy: new version is unhealthy and there is no previous release to roll back to" >&2
  fi
  return 1
}

# Waits until the URL answers 2xx.
healthy() {
  local url="$1" retries="$2" interval="$3"
  for ((i = 1; i <= retries; i++)); do
    if curl -fsS -o /dev/null --max-time 5 "$url"; then return 0; fi
    sleep "$interval"
  done
  echo "deploy: health check failed after $retries attempts" >&2
  return 1
}

# Keeps the newest $keep releases, never the live one or the one rolled back to.
prune() {
  local dir="$1" keep="$2" live="$3" previous="$4" f n=0
  while IFS= read -r f; do
    n=$((n + 1))
    if ((n > keep)) && [[ "$f" != "$live" && "$f" != "$previous" ]]; then rm -f "$f"; fi
  done < <(ls -1t "$dir"/cvfitai-*.jar 2>/dev/null)
}

# The whole file is parsed before running, so a later upload can safely replace this script mid-deploy.
main "$@"
