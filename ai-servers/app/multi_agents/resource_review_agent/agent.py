from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class ResourceReviewAgent:
    name = "resource_review_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )


resource_review_agent = ResourceReviewAgent()

__all__ = ["resource_review_agent"]
