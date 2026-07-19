from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class KnowledgeGraphPromptAgent:
    """Internal text-only specialist used by the knowledge-graph image tool."""

    name = "knowledge_graph_prompt_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(self.name, input_text, evidence, model_provider=chat_service)


knowledge_graph_prompt_agent = KnowledgeGraphPromptAgent()

__all__ = ["KnowledgeGraphPromptAgent", "knowledge_graph_prompt_agent"]
