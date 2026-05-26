from typing import Any, Dict, List


class PptAgent:
    name = "ppt_agent"

    def build_outline(self, topic: str, evidence: List[Dict[str, Any]], slide_count: int = 6) -> str:
        points = self._points(topic, evidence)
        lines = ["## PPT 大纲"]
        for index in range(slide_count):
            title = points[index] if index < len(points) else f"补充页 {index + 1}"
            lines.append(f"### 第 {index + 1} 页：{title[:36]}")
            lines.append("- 讲解目标：说明本页核心概念")
            lines.append("- 页面建议：标题 + 3 个要点 + 一个课堂例子")
        return "\n".join(lines)

    def _points(self, topic: str, evidence: List[Dict[str, Any]]) -> List[str]:
        result = [topic] if topic else []
        for item in evidence or []:
            content = str(item.get("content") or item.get("name") or "").strip()
            if content:
                result.append(content.replace("\n", " "))
        return result


ppt_agent = PptAgent()
