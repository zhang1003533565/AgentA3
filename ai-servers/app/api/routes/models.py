from typing import Any, Dict, Optional

from fastapi import APIRouter, Header, HTTPException

from app.model_providers.catalog import get_model_provider_catalog

router = APIRouter(prefix="/internal/models", tags=["internal-models"])


@router.get("/providers")
def list_model_providers(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
) -> Dict[str, Any]:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    return get_model_provider_catalog()
