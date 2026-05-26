from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from app.multi_agents.catalog import get_agent_profile, normalize_agent_name
from app.services.langchain_chat_service import get_chat_service
from app.services.memory_store import memory_store
from app.utils.text_utils import is_schedule_intent, is_smalltalk_intent


@dataclass
class LeaderPlan:
    intent: str
    target_agent: str
    need_retrieval: bool
    rag_strategy: str


class LeaderAgent:
    name = "leader_agent"

    def plan(self, input_text: str, rag_strategy: str = "", requested_agent: Optional[str] = None) -> LeaderPlan:
        forced_plan = self._plan_for_requested_agent(requested_agent, rag_strategy)
        if forced_plan:
            return forced_plan

        normalized = (input_text or "").strip().lower()
        if is_smalltalk_intent(input_text):
            return LeaderPlan("smalltalk", "leader_agent", False, rag_strategy or "naive_rag")
        if is_schedule_intent(input_text):
            return LeaderPlan("schedule", "textbook_knowledge_agent", True, rag_strategy or "naive_rag")
        if any(token in normalized for token in ("思维导图", "mindmap", "mind map", "脑图")):
            return LeaderPlan("mind_map", "mind_map_agent", True, rag_strategy or "multi_agent_rag")
        if any(token in normalized for token in ("题库", "练习题", "选择题", "判断题", "简答题", "试卷", "出题")):
            return LeaderPlan("question_bank", "textbook_question_bank_agent", True, rag_strategy or "multi_agent_rag")
        if any(token in normalized for token in ("ppt", "幻灯片", "课件", "演示文稿")):
            return LeaderPlan("ppt", "ppt_agent", True, rag_strategy or "multi_agent_rag")
        if any(token in normalized for token in ("图片", "配图", "插图", "海报", "封面图", "image")):
            return LeaderPlan("image", "image_agent", True, rag_strategy or "multimodal_rag")
        if any(token in normalized for token in ("md", "markdown", "知识点提取", "提取知识点", "知识点整理")):
            return LeaderPlan("md_knowledge", "md_knowledge_agent", False, rag_strategy or "semantic_chunking")
        if any(token in normalized for token in ("教材", "课本", "章节", "考点", "知识点", "课程内容")):
            return LeaderPlan("textbook_knowledge", "textbook_knowledge_agent", True, rag_strategy or "hybrid_search")
        return LeaderPlan("campus_search", "textbook_knowledge_agent", True, rag_strategy or "naive_rag")

    def _plan_for_requested_agent(self, requested_agent: Optional[str], rag_strategy: str) -> Optional[LeaderPlan]:
        agent_name = normalize_agent_name(requested_agent)
        if not agent_name:
            return None
        profile = get_agent_profile(agent_name)
        if not profile or agent_name == "leader_agent":
            return None
        return LeaderPlan(
            intent=profile["intent"],
            target_agent=agent_name,
            need_retrieval=bool(profile["needRetrieval"]),
            rag_strategy=rag_strategy or profile["defaultRagStrategy"],
        )

    def load_memory(self, session_token: str) -> List[Dict[str, str]]:
        return memory_store.get_history(session_token)

    def save_memory(self, session_token: str, user_input: str, assistant_answer: str) -> None:
        memory_store.append(session_token, user_input, assistant_answer)

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
        chat_service=None,
    ) -> str:
        service = chat_service or get_chat_service()
        return service.answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        ).strip()


leader_agent = LeaderAgent()
