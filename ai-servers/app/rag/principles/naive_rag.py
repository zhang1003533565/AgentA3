from dataclasses import dataclass
from typing import Any, Dict, List

from app.multi_agents import answer_agent, critic_agent, planner_agent, retriever_agent
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
        decision = planner_agent.decide(input_text)
        if decision.intent == "smalltalk":
            return decision.intent, ""
        if decision.intent == "schedule":
            return decision.intent, "课表查询"
        return decision.intent, get_chat_service().extract_search_keyword(input_text)

    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        if not keyword:
            return []
        return retriever_agent.retrieve(authorization, intent, keyword, input_text)

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        draft = answer_agent.answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        )
        return critic_agent.refine(draft)


naive_rag_strategy = NaiveRagStrategy()
