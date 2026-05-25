from typing import Any, Dict, List

from app.rag.retrievers import java_backend_retriever


class RetrieverAgent:
    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        if not keyword:
            return []
        if intent == "schedule":
            return java_backend_retriever.search_schedule(authorization, input_text)
        return java_backend_retriever.search_keyword(authorization, keyword)


retriever_agent = RetrieverAgent()
