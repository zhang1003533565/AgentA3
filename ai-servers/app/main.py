import secrets

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.api.routes.architecture import router as architecture_router
from app.api.routes.chat import router as chat_router
from app.api.routes.coding import router as coding_router
from app.api.routes.images import router as images_router
from app.api.routes.models import router as models_router
from app.api.routes.rag import export_router as rag_export_router
from app.api.routes.rag import router as rag_router
from app.api.routes.videos import router as videos_router
from app.ppt_generation import router as ppt_generation_router
from app.rag.document_conversion import EXPORT_ROOT
from app.security.internal_auth import get_configured_internal_token
from app.observability.langfuse import flush as flush_langfuse
from app.services.memory_store import memory_store
from app.utils.logger import init_logging

init_logging()
EXPORT_ROOT.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="ai-servers", version="0.1.0")
app.include_router(architecture_router)
app.include_router(chat_router)
app.include_router(coding_router)
app.include_router(images_router)
app.include_router(models_router)
app.include_router(rag_router)
app.include_router(rag_export_router)
app.include_router(videos_router)
app.include_router(ppt_generation_router)


@app.on_event("shutdown")
def flush_observability() -> None:
    flush_langfuse()


def _is_export_capability_route(method: str, path: str, raw_path: bytes = b"") -> bool:
    prefix = "/internal/rag/exports/"
    route_path = raw_path.decode("ascii", "ignore") if raw_path else path
    storage_key = route_path[len(prefix):] if route_path.startswith(prefix) else ""
    return method == "GET" and bool(storage_key) and "/" not in storage_key


@app.middleware("http")
async def require_internal_service_token(request, call_next):
    configured_token = get_configured_internal_token()
    requires_internal_token = (
        request.url.path.startswith("/internal")
        and not _is_export_capability_route(
            request.method,
            request.url.path,
            request.scope.get("raw_path", b""),
        )
    )
    if requires_internal_token:
        supplied_token = request.headers.get("X-AI-Internal-Token", "")
        if not secrets.compare_digest(supplied_token, configured_token):
            return JSONResponse(status_code=401, content={"detail": "内部服务凭据无效"})
    return await call_next(request)


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


@app.get("/internal/readiness")
def internal_readiness():
    redis_ready = memory_store.is_redis_ready()
    payload = {
        "status": "UP" if redis_ready else "DOWN",
        "redis": "UP" if redis_ready else "DOWN",
    }
    if redis_ready:
        return payload
    return JSONResponse(status_code=503, content=payload)
