from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.services.langchain_chat_service import get_chat_service


class MdKnowledgeAgent:
    name = "md_knowledge_agent"

    def extract_keyword(self, input_text: str, chat_service=None) -> str:
        service = chat_service or get_chat_service()
        keyword = (service.extract_search_keyword(input_text) or "").strip()
        if not keyword:
            raise HTTPException(status_code=502, detail="LLM 未返回检索关键词，已禁止本地关键词兜底")
        return keyword

    def extract_knowledge_points(
        self,
        markdown_text: str,
        evidence: Optional[List[Dict[str, Any]]] = None,
        chat_service=None,
    ) -> str:
        service = chat_service or get_chat_service()
        return service.generate_specialist_answer(self.name, markdown_text, evidence or [])


md_knowledge_agent = MdKnowledgeAgent()
