from fastapi import FastAPI

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


@app.get("/healthz")
def healthz():
    return {"status": "ok"}
