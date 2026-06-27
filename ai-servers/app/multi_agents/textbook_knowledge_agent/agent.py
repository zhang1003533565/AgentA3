from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise
from app.services.data_store import data_store


class TextbookKnowledgeAgent:
    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        results, _ = self.retrieve_with_meta(authorization, intent, keyword, input_text)
        return results

    def retrieve_with_meta(
        self,
        authorization: str,
        intent: str,
        keyword: str,
        input_text: str,
    ) -> tuple[List[Dict[str, Any]], Dict[str, Any]]:
        if not keyword and intent != "schedule":
            return [], self._meta(0)
        if intent == "schedule":
            results = data_store.search_schedule(authorization, input_text)
        else:
            results = data_store.search_keyword(authorization, keyword)
        return results, self._meta(len(results))

    def summarize_knowledge_points(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise("textbook_knowledge_agent", topic, evidence, model_provider=chat_service)

    def _meta(self, java_backend_count: int) -> Dict[str, Any]:
        return {
            "javaBackendCount": java_backend_count,
            "documentCount": 0,
            "localKnowledgeBase": False,
            "localRagStrategies": False,
        }


textbook_knowledge_agent = TextbookKnowledgeAgent()
