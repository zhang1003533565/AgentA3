from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class FileContentPlannerAgent:
    name = "file_content_planner_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(self.name, input_text, evidence, model_provider=chat_service)


file_content_planner_agent = FileContentPlannerAgent()
