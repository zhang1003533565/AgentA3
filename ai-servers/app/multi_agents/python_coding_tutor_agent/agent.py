from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class PythonCodingTutorAgent:
    name = "python_coding_tutor_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(
            self.name,
            input_text,
            evidence or [],
            model_provider=chat_service,
        )


python_coding_tutor_agent = PythonCodingTutorAgent()

__all__ = ["python_coding_tutor_agent"]
