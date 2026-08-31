from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class WeeklyJobRecommendationAgent:
    name = "weekly_job_recommendation_agent"

    def process(
        self,
        input_text: str,
        evidence: List[Dict[str, Any]],
        chat_service=None,
    ) -> str:
        return complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )


weekly_job_recommendation_agent = WeeklyJobRecommendationAgent()

__all__ = ["weekly_job_recommendation_agent"]
