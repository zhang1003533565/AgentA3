from typing import Any, Dict, List


class ToolAgent:
    name = "tool_agent"

    def select_tool(self, task: str, available_tools: List[str]) -> Dict[str, Any]:
        normalized = (task or "").lower()
        preference = [
            ("sql", "text_to_sql"),
            ("数据库", "text_to_sql"),
            ("图谱", "graph_query"),
            ("关系", "graph_query"),
            ("检索", "hybrid_retriever"),
            ("搜索", "hybrid_retriever"),
            ("向量", "vector_retriever"),
        ]
        selected = available_tools[0] if available_tools else ""
        for token, tool_name in preference:
            if token in normalized and tool_name in available_tools:
                selected = tool_name
                break
        return {"tool_name": selected, "tool_args": {"task": task}, "tool_result": None}


tool_agent = ToolAgent()
