# Auto Deploy

## What This Deploys

- `AppBackend`: Docker build + run
- `AppWeb`: Docker build + run
- `mysql`: runtime dependency
- `redis`: runtime dependency

`AppWeb` uses Nginx to proxy `/api`, `/uploads`, `/swagger-ui*`, `/v3/api-docs*`, and `/webjars/*` to `AppBackend`.

## Server Preparation

1. Install Docker and Docker Compose.
2. Clone this repository onto the server.
3. Copy `deploy/.env.example` to `deploy/.env`.
4. Fill real values in `deploy/.env`.
5. Run once on the server:

```bash
cd /path/to/AgentA3
chmod +x deploy/deploy-on-server.sh
./deploy/deploy-on-server.sh
```

## GitHub Secrets

Configure these repository secrets:

- `DEPLOY_HOST`: server IP or domain
- `DEPLOY_USER`: SSH user
- `DEPLOY_SSH_KEY`: private key content
- `DEPLOY_PORT`: SSH port, usually `22`
- `DEPLOY_PATH`: absolute repo path on the server, for example `/opt/AgentA3`

## Trigger

`.github/workflows/deploy.yml` triggers on:

- push to `main`
- push to `master`
- manual `workflow_dispatch`

## Notes

- `AI_PYTHON_BASE_URL` defaults to `http://host.docker.internal:8081`. If your Python AI service is elsewhere, change it in `deploy/.env`.
- Uploaded backend files are persisted in the Docker volume `smart-campus-prod_backend_uploads`.
- MySQL and Redis data are persisted in named Docker volumes.
