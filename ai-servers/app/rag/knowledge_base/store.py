from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from app.rag.knowledge_base.models import (
    DEFAULT_KNOWLEDGE_BASE_ID,
    DEFAULT_KNOWLEDGE_BASE_NAME,
    KnowledgeBase,
    KnowledgeBaseDocument,
    KnowledgeBaseSegment,
    ProcessRule,
    RetrievalConfig,
    utc_now_iso,
)


class KnowledgeBaseStore:
    def __init__(self, root_dir: Path) -> None:
        self.root_dir = Path(root_dir)
        self.meta_dir = self.root_dir / ".metadata"
        self.path = self.meta_dir / "knowledge_base_store.json"

    def ensure_default(self) -> KnowledgeBase:
        payload = self._read()
        datasets = payload.setdefault("knowledgeBases", {})
        if DEFAULT_KNOWLEDGE_BASE_ID not in datasets:
            knowledge_base = KnowledgeBase(
                id=DEFAULT_KNOWLEDGE_BASE_ID,
                name=DEFAULT_KNOWLEDGE_BASE_NAME,
                description="系统默认知识库，兼容旧版 RAG 上传与召回入口。",
            )
            datasets[DEFAULT_KNOWLEDGE_BASE_ID] = knowledge_base.to_dict()
            self._write(payload)
            return knowledge_base
        return KnowledgeBase.from_dict(datasets[DEFAULT_KNOWLEDGE_BASE_ID])

    def upsert_knowledge_base(
        self,
        knowledge_base_id: str,
        name: Optional[str] = None,
        description: str = "",
        process_rule: Optional[ProcessRule] = None,
        retrieval_config: Optional[RetrievalConfig] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> KnowledgeBase:
        normalized_id = self._normalize_id(knowledge_base_id)
        payload = self._read()
        datasets = payload.setdefault("knowledgeBases", {})
        existing = KnowledgeBase.from_dict(datasets[normalized_id]) if normalized_id in datasets else None
        now = utc_now_iso()
        knowledge_base = existing or KnowledgeBase(
            id=normalized_id,
            name=name or (DEFAULT_KNOWLEDGE_BASE_NAME if normalized_id == DEFAULT_KNOWLEDGE_BASE_ID else normalized_id),
            description=description,
        )
        if name:
            knowledge_base.name = name
        if description:
            knowledge_base.description = description
        if process_rule:
            knowledge_base.process_rule = process_rule
        if retrieval_config:
            knowledge_base.retrieval_config = retrieval_config
        if metadata:
            knowledge_base.metadata = {**knowledge_base.metadata, **metadata}
        knowledge_base.updated_at = now
        datasets[normalized_id] = knowledge_base.to_dict()
        self._write(payload)
        return knowledge_base

    def get_knowledge_base(self, knowledge_base_id: str) -> Optional[KnowledgeBase]:
        payload = self._read()
        row = payload.get("knowledgeBases", {}).get(self._normalize_id(knowledge_base_id))
        return KnowledgeBase.from_dict(row) if isinstance(row, dict) else None

    def list_knowledge_bases(self) -> List[Dict[str, Any]]:
        payload = self._read()
        documents = payload.get("documents", {})
        segments = payload.get("segments", {})
        result: List[Dict[str, Any]] = []
        for row in payload.get("knowledgeBases", {}).values():
            knowledge_base = KnowledgeBase.from_dict(row)
            kb_documents = [
                KnowledgeBaseDocument.from_dict(item)
                for item in documents.values()
                if item.get("knowledgeBaseId") == knowledge_base.id and not item.get("archived", False)
            ]
            kb_segments = [
                item for item in segments.values()
                if item.get("knowledgeBaseId") == knowledge_base.id and item.get("enabled", True)
            ]
            item = knowledge_base.to_dict()
            item.update({
                "documentCount": len(kb_documents),
                "availableDocumentCount": sum(1 for document in kb_documents if document.enabled and document.indexing_status == "completed"),
                "chunkCount": len(kb_segments),
                "size": sum(document.size for document in kb_documents),
            })
            result.append(item)
        result.sort(key=lambda item: (item.get("name") or "", item.get("id") or ""))
        return result

    def replace_document(
        self,
        document: KnowledgeBaseDocument,
        segments: Iterable[KnowledgeBaseSegment],
    ) -> None:
        payload = self._read()
        payload.setdefault("documents", {})
        payload.setdefault("segments", {})
        payload["documents"][document.id] = document.to_dict()
        payload["segments"] = {
            segment_id: row
            for segment_id, row in payload["segments"].items()
            if row.get("documentId") != document.id
        }
        for segment in segments:
            payload["segments"][segment.id] = segment.to_dict()
        self._write(payload)

    def list_documents(self, knowledge_base_ids: Optional[List[str]] = None) -> List[Dict[str, Any]]:
        payload = self._read()
        allowed = {self._normalize_id(item) for item in knowledge_base_ids or [] if item}
        knowledge_bases = payload.get("knowledgeBases", {})
        documents = []
        for row in payload.get("documents", {}).values():
            document = KnowledgeBaseDocument.from_dict(row)
            if allowed and document.knowledge_base_id not in allowed:
                continue
            knowledge_base = KnowledgeBase.from_dict(knowledge_bases.get(document.knowledge_base_id, {}))
            item = document.to_dict()
            item.update({
                "knowledgeBaseName": knowledge_base.name or DEFAULT_KNOWLEDGE_BASE_NAME,
                "chunkCount": document.segment_count,
                "vectorStoreBackend": document.metadata.get("vectorStoreBackend") or "",
                "collection": document.metadata.get("collection") or "",
                "updatedAt": document.updated_at,
            })
            documents.append(item)
        documents.sort(key=lambda item: (item.get("knowledgeBaseName") or "", item.get("name") or ""))
        return documents

    def list_segments(
        self,
        source: str = "",
        knowledge_base_ids: Optional[List[str]] = None,
        document_id: str = "",
    ) -> List[Dict[str, Any]]:
        payload = self._read()
        allowed = {self._normalize_id(item) for item in knowledge_base_ids or [] if item}
        segments = []
        for row in payload.get("segments", {}).values():
            segment = KnowledgeBaseSegment.from_dict(row)
            source_name = str(segment.metadata.get("sourceName") or segment.source or "")
            if source and source_name != source:
                continue
            if document_id and segment.document_id != document_id:
                continue
            if allowed and segment.knowledge_base_id not in allowed:
                continue
            item = segment.to_dict()
            item.update({
                "chunkIndex": segment.position,
                "storedPath": segment.source,
                "size": len(segment.content.encode("utf-8")),
                "metadata": segment.metadata,
            })
            segments.append(item)
        segments.sort(key=lambda item: (
            str(item.get("source") or ""),
            int(item.get("position") or 0),
            str(item.get("id") or ""),
        ))
        return segments

    def _read(self) -> Dict[str, Any]:
        if not self.path.exists():
            return {"version": 1, "knowledgeBases": {}, "documents": {}, "segments": {}}
        try:
            payload = json.loads(self.path.read_text(encoding="utf-8"))
        except Exception:
            return {"version": 1, "knowledgeBases": {}, "documents": {}, "segments": {}}
        if not isinstance(payload, dict):
            return {"version": 1, "knowledgeBases": {}, "documents": {}, "segments": {}}
        payload.setdefault("version", 1)
        payload.setdefault("knowledgeBases", {})
        payload.setdefault("documents", {})
        payload.setdefault("segments", {})
        return payload

    def _write(self, payload: Dict[str, Any]) -> None:
        self.meta_dir.mkdir(parents=True, exist_ok=True)
        self.path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def _normalize_id(self, value: str) -> str:
        normalized = str(value or "").strip()
        return normalized or DEFAULT_KNOWLEDGE_BASE_ID
