# Submission Auto Deploy

## Single deployment source

CI, server deployment and manual competition reproduction all use the repository-root `deploy/compose.submission.yml`. The manifest starts MySQL, Redis, AppBackend, the Python AI Server and AppWeb. The server script does not use the legacy backend-only Compose profile.

GitHub builds and pushes three application images. MySQL and Redis use the pinned images declared in the submission manifest. Runtime volumes preserve database data, Redis state, uploads, preview files and generated AI exports.

## Server preparation

1. Install Docker Engine and Docker Compose v2.
2. Clone this repository onto the server.
3. Copy `deploy/.env.example` to `deploy/.env`.
4. Replace every `CHANGE_ME` value with independent real secrets.
5. Run:

```bash
cd /path/to/AgentA3
chmod +x AppBackend/deploy/deploy-on-server.sh deploy/verify.sh
./AppBackend/deploy/deploy-on-server.sh
```

The script updates the selected branch, renders the submission manifest, runs the `config-guard`, pulls all six service images (including the guard image), starts the stack and executes `deploy/verify.sh`.

## GitHub configuration

Required repository secrets:

- `ACR_REGISTRY`, `ACR_USERNAME`, `ACR_PASSWORD`
- `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PORT`, `DEPLOY_PATH`
- `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, `AI_INTERNAL_TOKEN`

Optional repository variables `BACKEND_IMAGE`, `AI_SERVER_IMAGE` and `WEB_IMAGE` override the default ACR image paths. Every published image uses the immutable Git commit SHA as `IMAGE_TAG`. CI passes the same JWT and AI internal token values into Docker Compose; the latter is shared by Java and Python.

## Verification

The deployment fails before application startup when a required secret is empty, contains `CHANGE_ME`, or when the JWT/AI token is too short. After startup, the verifier checks:

- backend liveness and database/Redis/Java→Python readiness;
- Python liveness and, when the token is available, authenticated Python→Redis readiness;
- AppWeb reachability;
- the campus authentication boundary, plus a real authenticated business request when `SMOKE_TOKEN` is provided.

See `docs/deployment/submission-runbook.md` for manual commands, external MaxKB/model setup and troubleshooting.
