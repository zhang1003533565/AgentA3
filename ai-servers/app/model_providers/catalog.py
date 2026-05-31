import json
from copy import deepcopy
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict

from app.model_providers.deepseek.provider import DEEPSEEK_PROVIDER_CATALOG
from app.model_providers.qwen.provider import QWEN_PROVIDER_CATALOG
from app.model_providers.xiaomi.provider import XIAOMI_PROVIDER_CATALOG


CATALOG_PATH = Path(__file__).with_name("catalog.json")
SUPPORTED_MODALITIES = ("text", "image", "video")
PROVIDER_CATALOGS = {
    "deepseek": DEEPSEEK_PROVIDER_CATALOG,
    "qwen": QWEN_PROVIDER_CATALOG,
    "xiaomi": XIAOMI_PROVIDER_CATALOG,
}


@lru_cache(maxsize=1)
def _load_catalog() -> Dict[str, Any]:
    with CATALOG_PATH.open("r", encoding="utf-8") as fp:
        return json.load(fp)


def get_model_provider_catalog() -> Dict[str, Any]:
    catalog = deepcopy(_load_catalog())
    providers = []
    for item in catalog.get("providers", []):
        provider_id = str(item.get("id", "")).strip()
        provider_catalog = deepcopy(PROVIDER_CATALOGS.get(provider_id, {}))
        if not provider_catalog:
            continue
        provider_catalog.update({key: value for key, value in item.items() if value not in (None, "")})
        providers.append(provider_catalog)
    catalog["providers"] = providers
    catalog["modalities"] = [
        {"key": "text", "label": "文本", "requiredFields": ["provider", "base-url", "api-key", "model"]},
        {"key": "image", "label": "图片", "requiredFields": ["provider", "base-url", "api-key", "model"]},
        {"key": "video", "label": "视频", "requiredFields": ["provider", "base-url", "api-key", "model"]},
    ]
    catalog["total"] = len(providers)
    return catalog
