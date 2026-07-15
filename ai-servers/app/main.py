import os
import secrets

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.api.routes.chat import router as chat_router
from app.api.routes.images import router as images_router
from app.api.routes.models import router as models_router
from app.api.routes.rag import export_router as rag_export_router
from app.api.routes.rag import router as rag_router
from app.api.routes.videos import router as videos_router
from app.rag.document_conversion import EXPORT_ROOT
from app.utils.logger import init_logging

init_logging()
EXPORT_ROOT.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="ai-servers", version="0.1.0")
app.include_router(chat_router)
app.include_router(images_router)
app.include_router(models_router)
app.include_router(rag_router)
app.include_router(rag_export_router)
app.include_router(videos_router)


@app.middleware("http")
async def require_internal_service_token(request, call_next):
    configured_token = os.getenv("AI_INTERNAL_TOKEN", "").strip()
    if request.url.path.startswith("/internal") and configured_token:
        supplied_token = request.headers.get("X-AI-Internal-Token", "")
        if not secrets.compare_digest(supplied_token, configured_token):
            return JSONResponse(status_code=401, content={"detail": "内部服务凭据无效"})
    return await call_next(request)


@app.get("/healthz")
def healthz():
    return {"status": "ok"}
