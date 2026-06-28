from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class ProfileSummaryAgent:
    name = "profile_summary_agent"

    def process(self, profile_snapshot: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(self.name, profile_snapshot, evidence or [], model_provider=chat_service)


profile_summary_agent = ProfileSummaryAgent()

__all__ = ["profile_summary_agent"]
