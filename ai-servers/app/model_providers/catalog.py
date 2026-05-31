import json
from copy import deepcopy
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict


CATALOG_PATH = Path(__file__).with_name("catalog.json")
MODALITY_LABELS = {
    "text": "语言模型",
    "vision": "视觉/视频理解",
    "image": "图片生成/编辑",
    "video": "视频生成/编辑",
    "audio": "语音/音频",
}
REQUIRED_FIELDS = ["provider", "base-url", "api-key", "model"]


@lru_cache(maxsize=1)
def _load_catalog() -> Dict[str, Any]:
    with CATALOG_PATH.open("r", encoding="utf-8") as fp:
        return json.load(fp)


def get_model_provider_catalog() -> Dict[str, Any]:
    catalog = deepcopy(_load_catalog())
    providers = catalog.get("providers", [])
    modality_keys = []

    for provider in providers:
        capabilities = provider.get("capabilities") or {}
        for key, capability in capabilities.items():
            if key not in modality_keys:
                modality_keys.append(key)
            if isinstance(capability, dict):
                capability.setdefault("label", MODALITY_LABELS.get(key, key))
                capability.setdefault("status", "implemented")
                capability.setdefault("models", [])

    catalog["modalities"] = [
        {
            "key": key,
            "label": MODALITY_LABELS.get(key, key),
            "requiredFields": REQUIRED_FIELDS,
        }
        for key in modality_keys
    ]
    catalog["total"] = len(providers)
    return catalog
