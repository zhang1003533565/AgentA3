# ai-servers

Python FastAPI + LangChain internal service for AppBackend AI chat migration.

## Structure

- `app/main.py`: app bootstrap
- `app/api/routes/chat.py`: internal chat APIs
- `app/langgraph/graph/workflow.py`: graph orchestrator
- `app/langgraph/nodes/*`: node implementations
- `app/langgraph/state.py`: graph state
- `app/services/*`: orchestration, memory, retrieval, langchain calls
- `app/models/schemas.py`: request/response models
- `app/__init__.py`: load `.env`
- `app/utils/*`: text/sse helpers

## Endpoints

- `POST /internal/chat`
- `POST /internal/chat/stream` (SSE)
- `GET /healthz`

## Run

```bash
cd ai-servers
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port ${PYTHON_SERVER_PORT:-8081}
```

服务会自动读取 `ai-servers/.env`。

## Environment Variables

- `PYTHON_SERVER_PORT` default `8081`
- `DEEPSEEK_BASE_URL` default `https://api.deepseek.com`
- `DEEPSEEK_API_KEY` required
- `DEEPSEEK_MODEL` default `deepseek-chat`
- `REDIS_URL` default `redis://localhost:6379/0`
- `LLM_MEMORY_TTL_MINUTES` default `120`
- `LLM_MEMORY_MAX_MESSAGES` default `20`
- `JAVA_BACKEND_BASE_URL` default `http://localhost:8080`
- `JAVA_BACKEND_TIMEOUT_SECONDS` default `8`

`ai-servers` 的业务数据（食堂/档口/菜品/优惠券/课表）通过 `Authorization` 复用 Java 现有接口获取，不直接连数据库。
