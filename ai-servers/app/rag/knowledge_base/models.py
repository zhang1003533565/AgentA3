from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

DEFAULT_KNOWLEDGE_BASE_ID = "default"
DEFAULT_KNOWLEDGE_BASE_NAME = "默认知识库"


def utc_now_iso() -> str:
    from datetime import datetime, timezone

    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


@dataclass
class RetrievalConfig:
    method: str = "hybrid_search"
    top_k: int = 5
    score_threshold: float = 0.0
    vector_weight: float = 0.7
    keyword_weight: float = 0.3
    reranking_enabled: bool = False
    metadata_filter: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, payload: Optional[Dict[str, Any]]) -> "RetrievalConfig":
        if not isinstance(payload, dict):
            return cls()
        return cls(
            method=str(payload.get("method") or payload.get("ragStrategy") or "hybrid_search"),
            top_k=_bounded_int(payload.get("topK") or payload.get("top_k"), 5, 1, 50),
            score_threshold=_bounded_float(payload.get("scoreThreshold") or payload.get("score_threshold"), 0.0, 0.0, 1.0),
            vector_weight=_bounded_float(payload.get("vectorWeight") or payload.get("vector_weight"), 0.7, 0.0, 1.0),
            keyword_weight=_bounded_float(payload.get("keywordWeight") or payload.get("keyword_weight"), 0.3, 0.0, 1.0),
            reranking_enabled=bool(payload.get("rerankingEnabled") or payload.get("reranking_enabled") or False),
            metadata_filter=payload.get("metadataFilter") if isinstance(payload.get("metadataFilter"), dict) else {},
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "method": self.method,
            "topK": self.top_k,
            "scoreThreshold": self.score_threshold,
            "vectorWeight": self.vector_weight,
            "keywordWeight": self.keyword_weight,
            "rerankingEnabled": self.reranking_enabled,
            "metadataFilter": self.metadata_filter,
        }


@dataclass
class ProcessRule:
    mode: str = "automatic"
    index_structure: str = "text_model"
    chunk_structure: str = "general"
    pre_processing_rules: List[Dict[str, Any]] = field(default_factory=lambda: [
        {"id": "remove_extra_spaces", "enabled": True},
        {"id": "remove_urls_emails", "enabled": False},
    ])
    segmentation: Dict[str, Any] = field(default_factory=lambda: {
        "separator": "\n\n",
        "maxTokens": 800,
        "chunkOverlap": 120,
    })
    parent_child: Dict[str, Any] = field(default_factory=lambda: {
        "parentMode": "paragraph",
        "parentChunkSize": 1600,
        "parentChunkOverlap": 160,
        "childChunkSize": 420,
        "childChunkOverlap": 80,
    })

    @classmethod
    def from_dict(cls, payload: Optional[Dict[str, Any]]) -> "ProcessRule":
        if not isinstance(payload, dict):
            return cls()
        rule = cls()
        rule.mode = str(payload.get("mode") or rule.mode)
        rule.index_structure = str(payload.get("indexStructure") or payload.get("index_structure") or rule.index_structure)
        rule.chunk_structure = str(payload.get("chunkStructure") or payload.get("chunk_structure") or rule.chunk_structure)
        if isinstance(payload.get("preProcessingRules"), list):
            rule.pre_processing_rules = payload["preProcessingRules"]
        if isinstance(payload.get("segmentation"), dict):
            rule.segmentation = {**rule.segmentation, **payload["segmentation"]}
        if isinstance(payload.get("parentChild"), dict):
            rule.parent_child = {**rule.parent_child, **payload["parentChild"]}
        return rule

    def to_dict(self) -> Dict[str, Any]:
        return {
            "mode": self.mode,
            "indexStructure": self.index_structure,
            "chunkStructure": self.chunk_structure,
            "preProcessingRules": self.pre_processing_rules,
            "segmentation": self.segmentation,
            "parentChild": self.parent_child,
        }


@dataclass
class KnowledgeBase:
    id: str
    name: str
    description: str = ""
    provider: str = "vendor"
    permission: str = "team"
    indexing_technique: str = "high_quality"
    embedding_model_provider: str = "local_lexical"
    embedding_model: str = "local_lexical"
    process_rule: ProcessRule = field(default_factory=ProcessRule)
    retrieval_config: RetrievalConfig = field(default_factory=RetrievalConfig)
    created_at: str = field(default_factory=utc_now_iso)
    updated_at: str = field(default_factory=utc_now_iso)
    metadata: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "KnowledgeBase":
        return cls(
            id=str(payload.get("id") or DEFAULT_KNOWLEDGE_BASE_ID),
            name=str(payload.get("name") or DEFAULT_KNOWLEDGE_BASE_NAME),
            description=str(payload.get("description") or ""),
            provider=str(payload.get("provider") or "vendor"),
            permission=str(payload.get("permission") or "team"),
            indexing_technique=str(payload.get("indexingTechnique") or payload.get("indexing_technique") or "high_quality"),
            embedding_model_provider=str(payload.get("embeddingModelProvider") or payload.get("embedding_model_provider") or "local_lexical"),
            embedding_model=str(payload.get("embeddingModel") or payload.get("embedding_model") or "local_lexical"),
            process_rule=ProcessRule.from_dict(payload.get("processRule") or payload.get("process_rule")),
            retrieval_config=RetrievalConfig.from_dict(payload.get("retrievalConfig") or payload.get("retrieval_config")),
            created_at=str(payload.get("createdAt") or payload.get("created_at") or utc_now_iso()),
            updated_at=str(payload.get("updatedAt") or payload.get("updated_at") or utc_now_iso()),
            metadata=payload.get("metadata") if isinstance(payload.get("metadata"), dict) else {},
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "name": self.name,
            "description": self.description,
            "provider": self.provider,
            "permission": self.permission,
            "indexingTechnique": self.indexing_technique,
            "embeddingModelProvider": self.embedding_model_provider,
            "embeddingModel": self.embedding_model,
            "processRule": self.process_rule.to_dict(),
            "retrievalConfig": self.retrieval_config.to_dict(),
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "metadata": self.metadata,
        }


@dataclass
class KnowledgeBaseDocument:
    id: str
    knowledge_base_id: str
    name: str
    source: str
    stored_path: str
    data_source_type: str = "upload_file"
    modality: str = "text"
    indexing_status: str = "completed"
    enabled: bool = True
    archived: bool = False
    word_count: int = 0
    token_count: int = 0
    segment_count: int = 0
    size: int = 0
    error: str = ""
    created_at: str = field(default_factory=utc_now_iso)
    updated_at: str = field(default_factory=utc_now_iso)
    metadata: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "KnowledgeBaseDocument":
        return cls(
            id=str(payload.get("id") or ""),
            knowledge_base_id=str(payload.get("knowledgeBaseId") or payload.get("knowledge_base_id") or DEFAULT_KNOWLEDGE_BASE_ID),
            name=str(payload.get("name") or payload.get("source") or ""),
            source=str(payload.get("source") or ""),
            stored_path=str(payload.get("storedPath") or payload.get("stored_path") or ""),
            data_source_type=str(payload.get("dataSourceType") or payload.get("data_source_type") or "upload_file"),
            modality=str(payload.get("modality") or "text"),
            indexing_status=str(payload.get("indexingStatus") or payload.get("indexing_status") or "completed"),
            enabled=bool(payload.get("enabled", True)),
            archived=bool(payload.get("archived", False)),
            word_count=int(payload.get("wordCount") or payload.get("word_count") or 0),
            token_count=int(payload.get("tokenCount") or payload.get("token_count") or 0),
            segment_count=int(payload.get("segmentCount") or payload.get("segment_count") or 0),
            size=int(payload.get("size") or 0),
            error=str(payload.get("error") or ""),
            created_at=str(payload.get("createdAt") or payload.get("created_at") or utc_now_iso()),
            updated_at=str(payload.get("updatedAt") or payload.get("updated_at") or utc_now_iso()),
            metadata=payload.get("metadata") if isinstance(payload.get("metadata"), dict) else {},
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "knowledgeBaseId": self.knowledge_base_id,
            "name": self.name,
            "source": self.source,
            "storedPath": self.stored_path,
            "dataSourceType": self.data_source_type,
            "modality": self.modality,
            "indexingStatus": self.indexing_status,
            "enabled": self.enabled,
            "archived": self.archived,
            "wordCount": self.word_count,
            "tokenCount": self.token_count,
            "segmentCount": self.segment_count,
            "size": self.size,
            "error": self.error,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "metadata": self.metadata,
        }


@dataclass
class KnowledgeBaseSegment:
    id: str
    document_id: str
    knowledge_base_id: str
    position: int
    content: str
    source: str
    status: str = "completed"
    enabled: bool = True
    word_count: int = 0
    token_count: int = 0
    hit_count: int = 0
    parent_id: Optional[str] = None
    child_count: int = 0
    created_at: str = field(default_factory=utc_now_iso)
    updated_at: str = field(default_factory=utc_now_iso)
    metadata: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "KnowledgeBaseSegment":
        return cls(
            id=str(payload.get("id") or ""),
            document_id=str(payload.get("documentId") or payload.get("document_id") or ""),
            knowledge_base_id=str(payload.get("knowledgeBaseId") or payload.get("knowledge_base_id") or DEFAULT_KNOWLEDGE_BASE_ID),
            position=int(payload.get("position") or 0),
            content=str(payload.get("content") or ""),
            source=str(payload.get("source") or ""),
            status=str(payload.get("status") or "completed"),
            enabled=bool(payload.get("enabled", True)),
            word_count=int(payload.get("wordCount") or payload.get("word_count") or 0),
            token_count=int(payload.get("tokenCount") or payload.get("token_count") or 0),
            hit_count=int(payload.get("hitCount") or payload.get("hit_count") or 0),
            parent_id=payload.get("parentId") or payload.get("parent_id"),
            child_count=int(payload.get("childCount") or payload.get("child_count") or 0),
            created_at=str(payload.get("createdAt") or payload.get("created_at") or utc_now_iso()),
            updated_at=str(payload.get("updatedAt") or payload.get("updated_at") or utc_now_iso()),
            metadata=payload.get("metadata") if isinstance(payload.get("metadata"), dict) else {},
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "documentId": self.document_id,
            "knowledgeBaseId": self.knowledge_base_id,
            "position": self.position,
            "content": self.content,
            "source": self.source,
            "status": self.status,
            "enabled": self.enabled,
            "wordCount": self.word_count,
            "tokenCount": self.token_count,
            "hitCount": self.hit_count,
            "parentId": self.parent_id,
            "childCount": self.child_count,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "metadata": self.metadata,
        }


def _bounded_int(value: Any, default: int, minimum: int, maximum: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        number = default
    return max(minimum, min(maximum, number))


def _bounded_float(value: Any, default: float, minimum: float, maximum: float) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        number = default
    return max(minimum, min(maximum, number))
