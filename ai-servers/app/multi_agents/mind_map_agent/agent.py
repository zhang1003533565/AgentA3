from typing import Any, Dict, List


class MindMapAgent:
    name = "mind_map_agent"

    def build_mind_map(self, topic: str, evidence: List[Dict[str, Any]]) -> str:
        title = (topic or "知识主题").strip()[:40]
        points = self._points(evidence)
        if not points:
            points = ["核心概念", "关键流程", "应用场景", "复习要点"]
        lines = ["```mermaid", "mindmap", f"  root(({self._escape(title)}))"]
        for index, point in enumerate(points[:8], start=1):
            lines.append(f"    {self._escape(str(index) + '. ' + point[:32])}")
        lines.append("```")
        return "\n".join(lines)

    def _points(self, evidence: List[Dict[str, Any]]) -> List[str]:
        result = []
        for item in evidence or []:
            content = str(item.get("content") or item.get("name") or "").strip()
            if content:
                result.append(content.replace("\n", " "))
        return result

    def _escape(self, value: str) -> str:
        return value.replace("(", "").replace(")", "").replace(":", "：")


mind_map_agent = MindMapAgent()
