# AppWeb

React + Vite management web for AgentA3.

## API target modes

The web client resolves its backend base URL through `VITE_API_MODE`.

| Mode | Result | Use case |
| --- | --- | --- |
| `local` | `http://localhost:8080` unless `VITE_API_BASE_URL` is set | Local development, default |
| `relative` | empty base URL, so `/api/**` stays on the current host | Single-server Docker/Nginx deployment |
| `remote` | `VITE_API_BASE_URL` | Temporary browser-to-remote-backend debugging |

Local development can copy:

```bash
cp .env.local.example .env.local
npm run dev
```

Single-server Docker images build with `VITE_API_MODE=relative`, so deployed browsers call the same host and Nginx proxies `/api` to Java backend.
