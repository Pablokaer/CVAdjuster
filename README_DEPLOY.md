# Deploy

## Produção (cvfitai.com) — automático via GitHub Actions

Todo push na `main` dispara `.github/workflows/deploy.yml`:

1. **Build** — `mvn verify` com JDK 21 (também roda em pull requests, sem deploy).
2. **Deploy** — o jar é enviado por SSH para a VPS e instalado por `deploy/deploy.sh`, que guarda o
   jar em `/opt/cvfitai/releases/`, aponta `current.jar` para ele, reinicia o serviço `cvfitai`
   (systemd) e espera `/login` responder. Se a nova versão não ficar saudável, volta sozinho para a
   release anterior e o job falha.
3. **Smoke test** — `https://cvfitai.com/login` precisa responder 200.

Também dá para rodar manualmente em *Actions → CI / Deploy → Run workflow*.

### Na VPS

| Item | Onde |
| --- | --- |
| Releases / jar atual | `/opt/cvfitai/releases/`, `/opt/cvfitai/current.jar` |
| Script de deploy | `/opt/cvfitai/deploy.sh` (cópia de `deploy/deploy.sh`) |
| Serviço | `/etc/systemd/system/cvfitai.service` (cópia de `deploy/cvfitai.service`) |
| Variáveis de ambiente (segredos) | `/home/cvfitai/.env` — fora do git, lido pelo systemd |
| Log do app | `journalctl -u cvfitai -f` |
| Log de deploy | `/var/log/cvfitai-deploy.log` |

A chave SSH do GitHub Actions só consegue executar `deploy.sh` (`command=` + `restrict` no
`authorized_keys`). Mudanças em `deploy/deploy.sh` ou `deploy/cvfitai.service` **não** são aplicadas
automaticamente: copie os arquivos para a VPS quando alterá-los.

### Secrets do repositório (Settings → Secrets and variables → Actions)

`VPS_HOST`, `VPS_USER`, `VPS_DEPLOY_KEY` (chave privada), `VPS_HOST_KEY` (chave pública do host, no
formato `ssh-ed25519 AAAA…`). O job usa o environment `production`.

### Rollback manual

```bash
ls -1t /opt/cvfitai/releases/
ln -sfn /opt/cvfitai/releases/cvfitai-<release>.jar /opt/cvfitai/current.jar
systemctl restart cvfitai
```

## Local (Docker)

```powershell
docker compose up -d --build
```

Usa o `.env` da raiz (não comitar). Veja o `README.md`.

## Segurança

- Nunca comite `.env`.
- Faça backup do banco antes de rodar migrações em produção.
