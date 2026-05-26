from typing import Any, Dict, List


class ImageAgent:
    name = "image_agent"

    def build_image_prompt(self, topic: str, evidence: List[Dict[str, Any]]) -> str:
        refs = [str(item.get("content") or item.get("name") or "").strip() for item in (evidence or [])]
        refs = [item.replace("\n", " ")[:80] for item in refs if item]
        lines = ["## 图片智能体提示词", f"主题：{topic or '课程知识配图'}"]
        if refs:
            lines.append("参考知识：")
            lines.extend(f"- {item}" for item in refs[:5])
        lines.append("视觉要求：清晰、教学导向、适合课堂或教材配图，避免无关装饰。")
        return "\n".join(lines)


image_agent = ImageAgent()
