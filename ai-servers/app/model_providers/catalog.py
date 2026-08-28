import json
from copy import deepcopy
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict


CATALOG_PATH = Path(__file__).with_name("catalog.json")
MODALITY_LABELS = {
    "text": "语言模型",
    "vision": "视觉/视频理解",
    "embedding": "向量模型",
    "image": "图片生成/编辑",
    "video": "视频生成/编辑",
    "audio": "语音/音频",
}
REQUIRED_FIELDS = ["provider", "base-url", "api-key", "model"]


@lru_cache(maxsize=1)
def _load_catalog() -> Dict[str, Any]:
    with CATALOG_PATH.open("r", encoding="utf-8") as fp:
        return json.load(fp)


def _provider_aliases(provider: Dict[str, Any]) -> set[str]:
    aliases = {str(provider.get("id") or "").strip().lower()}
    for alias in provider.get("aliases") or []:
        text = str(alias or "").strip().lower()
        if text:
            aliases.add(text)
    return aliases


def model_supports_vision(provider: str, model: str) -> bool:
    """Return whether a provider/model pair is cataloged with vision capability."""
    normalized_provider = str(provider or "").strip().lower()
    model_id = str(model or "").strip().lower()
    if not model_id:
        return False

    for prov in _load_catalog().get("providers", []):
        if normalized_provider and normalized_provider not in _provider_aliases(prov):
            continue
        for capability in (prov.get("capabilities") or {}).values():
            if not isinstance(capability, dict):
                continue
            for entry in capability.get("models") or []:
                if str(entry.get("id") or "").strip().lower() != model_id:
                    continue
                features = {str(feature).strip().lower() for feature in (entry.get("features") or [])}
                if "vision" in features or "image_understanding" in features or "full_modal_understanding" in features:
                    return True

    vision_markers = ("vision", "-vl", "vl-", "gpt-4o", "gpt-4.1", "gemini")
    return any(marker in model_id for marker in vision_markers)


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
