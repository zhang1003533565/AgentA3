# Auto Deploy

## What This Deploys

- `AppBackend`: GitHub builds image and server pulls it
- `AppWeb`: GitHub builds image and server pulls it
- `mysql`: runtime dependency
- `redis`: runtime dependency

`AppWeb` uses Nginx to proxy `/api`, `/uploads`, `/swagger-ui*`, `/v3/api-docs*`, and `/webjars/*` to `AppBackend`.
Deployment reuses `AppBackend/docker-compose.yml`, and starts `backend` / `web` through the `deploy` profile.
The server does not build app images locally anymore.

## Server Preparation

1. Install Docker and Docker Compose.
2. Clone this repository onto the server.
3. Copy `AppBackend/deploy/.env.example` to `AppBackend/deploy/.env`.
4. Fill real values in `AppBackend/deploy/.env`.
5. Run once on the server:

```bash
cd /path/to/AgentA3
chmod +x AppBackend/deploy/deploy-on-server.sh
./AppBackend/deploy/deploy-on-server.sh
```

## GitHub Secrets

Configure these repository secrets:

- `ACR_REGISTRY`: `crpi-awzm63dqn5ugddo8.cn-hangzhou.personal.cr.aliyuncs.com`
- `ACR_USERNAME`: `aliyun1551583868`
- `ACR_PASSWORD`: your ACR password
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

Workflow actions:

- build backend image
- build web image
- push both images to ACR
- SSH to the server
- `docker compose pull`
- `docker compose up -d`

## Notes

- `AI_PYTHON_BASE_URL` defaults to `http://host.docker.internal:8081`. If your Python AI service is elsewhere, change it in `AppBackend/deploy/.env`.
- `BACKEND_IMAGE`, `WEB_IMAGE`, and `IMAGE_TAG` are configured in `AppBackend/deploy/.env`.
- Uploaded files are persisted in `AppBackend/uploads`.
- MySQL and Redis data are persisted in `AppBackend/.data` and `AppBackend/.redis-data`.
