import re
from typing import List, Set

from app.rag.core.types import RagDocument
from app.rag.graph_stores.base import BaseGraphStore


class LocalGraphStore(BaseGraphStore):
    name = "local_graph"

    def search_paths(self, query: str, documents: List[RagDocument], top_k: int = 5) -> List[RagDocument]:
        query_entities = set(self._extract_entities(query))
        if not query_entities:
            return []
        scored: List[RagDocument] = []
        for document in documents:
            entities = self._extract_entities(document.content)
            score = len(query_entities & set(entities)) / max(len(query_entities), 1)
            if score <= 0:
                continue
            path = " -> ".join(entities[:4])
            scored.append(RagDocument(
                id=f"{document.id}#graph",
                content=f"图谱证据路径：{path}\n证据片段：{document.content}",
                source=document.source,
                score=score,
                metadata={
                    **document.metadata,
                    "entities": entities[:10],
                    "path": path,
                    "graphScore": score,
                    "graphBackend": self.name,
                },
            ))
        scored.sort(key=lambda item: item.score or 0.0, reverse=True)
        return scored[:top_k]

    def _extract_entities(self, text: str) -> List[str]:
        normalized = text or ""
        chinese_terms = re.findall(r"[\u4e00-\u9fff]{2,12}", normalized)
        chinese_chars = re.findall(r"[\u4e00-\u9fff]", normalized)
        chinese_bigrams = [left + right for left, right in zip(chinese_chars, chinese_chars[1:])]
        english_terms = re.findall(r"[a-zA-Z][a-zA-Z0-9_]{2,}", normalized)
        return self._unique(chinese_terms + chinese_bigrams + english_terms)

    def _unique(self, values: List[str]) -> List[str]:
        seen: Set[str] = set()
        result: List[str] = []
        for value in values:
            normalized = value.strip()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            result.append(normalized)
        return result
