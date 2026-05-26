import re
from typing import Any, Dict, List, Optional

from app.services.langchain_chat_service import get_chat_service


class MdKnowledgeAgent:
    name = "md_knowledge_agent"

    def extract_keyword(self, input_text: str, chat_service=None) -> str:
        service = chat_service or get_chat_service()
        try:
            keyword = service.extract_search_keyword(input_text)
        except Exception:
            keyword = ""
        return keyword or self._fallback_keyword(input_text)

    def extract_knowledge_points(self, markdown_text: str, evidence: Optional[List[Dict[str, Any]]] = None) -> str:
        text = markdown_text or ""
        headings = re.findall(r"^#{1,6}\s+(.+)$", text, flags=re.MULTILINE)
        bullets = re.findall(r"^\s*[-*+]\s+(.+)$", text, flags=re.MULTILINE)
        numbered = re.findall(r"^\s*\d+[.)]\s+(.+)$", text, flags=re.MULTILINE)
        evidence_points = [str(item.get("content", "")).strip() for item in (evidence or []) if item.get("content")]
        candidates = headings + bullets + numbered + evidence_points
        points = self._unique([self._clean(item) for item in candidates if self._clean(item)])[:12]
        if not points and text.strip():
            points = [self._clean(part) for part in re.split(r"[。！？\n]", text) if self._clean(part)][:8]
        if not points:
            return "暂无可提取的 Markdown 知识点。"
        lines = ["## Markdown 知识点提取"]
        lines.extend(f"- {point}" for point in points)
        return "\n".join(lines)

    def _fallback_keyword(self, input_text: str) -> str:
        cleaned = re.sub(r"[^\w\u4e00-\u9fff]+", " ", input_text or "").strip()
        return " ".join(cleaned.split()[:6])[:64]

    def _clean(self, value: str) -> str:
        return re.sub(r"\s+", " ", value or "").strip(" -#`|：:")

    def _unique(self, values: List[str]) -> List[str]:
        seen = set()
        result = []
        for value in values:
            if value in seen:
                continue
            seen.add(value)
            result.append(value)
        return result


md_knowledge_agent = MdKnowledgeAgent()
