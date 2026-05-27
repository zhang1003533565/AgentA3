from dataclasses import dataclass
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.leader_agent.agent import leader_agent
from app.multi_agents.textbook_knowledge_agent.agent import textbook_knowledge_agent
from app.services.langchain_chat_service import get_chat_service


@dataclass
class NaiveRagResult:
    intent: str
    keyword: str
    matched_results: List[Dict[str, Any]]
    answer: str


class NaiveRagStrategy:
    name = "naive_rag"

    def extract_keyword(self, input_text: str) -> tuple[str, str]:
        plan = leader_agent.plan(input_text, self.name)
        if plan.intent == "smalltalk":
            return plan.intent, ""
        if plan.intent == "schedule":
            return plan.intent, "课表查询"
        keyword = (get_chat_service().extract_search_keyword(input_text) or "").strip()
        if not keyword:
            raise HTTPException(status_code=502, detail="LLM 未返回检索关键词，已禁止本地关键词兜底")
        return plan.intent, keyword

    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        if not keyword:
            return []
        return textbook_knowledge_agent.retrieve(authorization, intent, keyword, input_text)

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        return leader_agent.answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        )


naive_rag_strategy = NaiveRagStrategy()
