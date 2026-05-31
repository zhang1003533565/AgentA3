import json
from copy import deepcopy
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict


CATALOG_PATH = Path(__file__).with_name("catalog.json")
SUPPORTED_MODALITIES = ("text", "image", "video")


@lru_cache(maxsize=1)
def _load_catalog() -> Dict[str, Any]:
    with CATALOG_PATH.open("r", encoding="utf-8") as fp:
        return json.load(fp)


def get_model_provider_catalog() -> Dict[str, Any]:
    catalog = deepcopy(_load_catalog())
    providers = catalog.get("providers", [])
    catalog["modalities"] = [
        {"key": "text", "label": "文本", "requiredFields": ["provider", "base-url", "api-key", "model"]},
        {"key": "image", "label": "图片", "requiredFields": ["provider", "base-url", "api-key", "model"]},
        {"key": "video", "label": "视频", "requiredFields": ["provider", "base-url", "api-key", "model"]},
    ]
    catalog["total"] = len(providers)
    return catalog
