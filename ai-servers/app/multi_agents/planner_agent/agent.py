from dataclasses import dataclass

from app.utils.text_utils import is_schedule_intent, is_smalltalk_intent


@dataclass
class PlanDecision:
    intent: str
    need_retrieval: bool


class PlannerAgent:
    def decide(self, input_text: str) -> PlanDecision:
        normalized = (input_text or "").strip().lower()
        if is_smalltalk_intent(input_text):
            return PlanDecision(intent="smalltalk", need_retrieval=False)
        if is_schedule_intent(input_text):
            return PlanDecision(intent="schedule", need_retrieval=True)
        if any(token in normalized for token in ("sql", "数据库", "统计", "多少个", "数量", "排名", "列表")):
            return PlanDecision(intent="text_to_sql", need_retrieval=True)
        if any(token in normalized for token in ("图谱", "关系", "关联", "路径", "实体")):
            return PlanDecision(intent="graph_rag", need_retrieval=True)
        if any(token in normalized for token in ("图片", "照片", "表格", "pdf", "附件", "截图", "多模态")):
            return PlanDecision(intent="multimodal_rag", need_retrieval=True)
        return PlanDecision(intent="campus_search", need_retrieval=True)


planner_agent = PlannerAgent()
