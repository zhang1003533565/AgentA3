from dataclasses import dataclass

from app.utils.text_utils import is_schedule_intent, is_smalltalk_intent


@dataclass
class PlanDecision:
    intent: str
    need_retrieval: bool


class PlannerAgent:
    def decide(self, input_text: str) -> PlanDecision:
        if is_smalltalk_intent(input_text):
            return PlanDecision(intent="smalltalk", need_retrieval=False)
        if is_schedule_intent(input_text):
            return PlanDecision(intent="schedule", need_retrieval=True)
        return PlanDecision(intent="campus_search", need_retrieval=True)


planner_agent = PlannerAgent()
