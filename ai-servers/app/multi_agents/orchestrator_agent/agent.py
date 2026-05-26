from typing import Any, Dict, List


class OrchestratorAgent:
    name = "orchestrator_agent"

    def plan(self, user_query: str, rag_strategy: str = "naive_rag") -> Dict[str, Any]:
        agents = self.agents_for_strategy(rag_strategy)
        return {
            "query": user_query,
            "ragStrategy": rag_strategy,
            "agents": agents,
            "steps": [
                {"stage": "plan", "agent": "planner_agent"},
                {"stage": "retrieve", "agent": "retriever_agent"},
                {"stage": "answer", "agent": "answer_agent"},
                {"stage": "critic", "agent": "critic_agent"},
            ],
        }

    def agents_for_strategy(self, rag_strategy: str) -> List[str]:
        base_agents = ["planner_agent", "retriever_agent", "answer_agent", "critic_agent", "memory_agent"]
        if rag_strategy == "text_to_sql":
            return ["planner_agent", "sql_agent", "answer_agent", "critic_agent", "memory_agent"]
        if rag_strategy == "graph_rag":
            return ["planner_agent", "graph_agent", "retriever_agent", "answer_agent", "critic_agent", "memory_agent"]
        if rag_strategy in {"agentic_rag", "multi_agent_rag"}:
            return ["orchestrator_agent", "planner_agent", "tool_agent", "retriever_agent", "answer_agent", "critic_agent", "memory_agent"]
        return base_agents


orchestrator_agent = OrchestratorAgent()
