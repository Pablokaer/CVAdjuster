Deploy rápido — passos essenciais

1) Verificar encoding dos recursos

  # Dry run: lista arquivos que seriam convertidos
  PowerShell:
  ```powershell
  .\scripts\convert-encoding.ps1 -DryRun
  ```

  # Converter para UTF-8 (cria backups .bak)
  ```powershell
  .\scripts\convert-encoding.ps1
  ```

2) Build local com Maven (JDK 21 necessário)

  ```powershell
  mvn -B -DskipTests package
  ```

3) Build da imagem Docker

  ```powershell
  docker build --progress=plain -t resumetailor:latest .
  ```

4) Rodar local usando arquivo de ambiente (não comitar .env)

  ```powershell
  docker run -d --name resumetailor -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod resumetailor:latest
  ```

5) Deploy com docker-compose (exemplo)

  ```powershell
  docker-compose -f docker-compose.prod.yml --env-file .env up -d --build
  ```

6) CI/CD (resumo)

  - Configure secrets no seu provider (Docker Hub, GHCR) e no GitHub Actions
  - Workflow típico: checkout -> setup-java (jdk21) -> mvn package -> docker/build-push-action -> push

Notas de segurança
  - Nunca comite `.env` com segredos
  - Preferir secret manager / Docker secrets / Kubernetes Secrets em produção
  - Backup do banco antes de rodar migrações em produção

