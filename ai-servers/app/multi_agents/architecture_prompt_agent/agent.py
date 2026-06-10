import re
from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


ARCHITECTURE_PROMPT_REQUIRED_FIELDS = [
    "图类型：",
    "核心目标：",
    "节点清单：",
    "关系清单：",
    "分层分组：",
    "布局要求：",
    "风格要求：",
    "必须包含文案：",
    "禁止事项：",
    "最终出图提示词：",
]


class ArchitecturePromptAgent:
    name = "architecture_prompt_agent"

    def build_architecture_prompt(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        """生成可手动传递给架构图智能体的稳定提示词模板。"""
        answer = complete_agent_or_raise(self.name, topic, evidence, model_provider=chat_service)
        return normalize_architecture_prompt_answer(answer, topic)

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return self.build_architecture_prompt(input_text, evidence, chat_service=chat_service)


architecture_prompt_agent = ArchitecturePromptAgent()


def normalize_architecture_prompt_answer(text: str, input_text: str = "") -> str:
    answer = _clean_transport_noise(text or "")
    answer = _strip_code_fences(answer)
    if _is_valid_architecture_prompt(answer):
        return answer.strip()
    return _rewrite_architecture_prompt(answer, input_text).strip()


def _clean_transport_noise(text: str) -> str:
    cleaned_lines = []
    for line in (text or "").splitlines():
        if "Connection #" in line and "left intact" in line:
            continue
        cleaned_lines.append(line.rstrip())
    return "\n".join(cleaned_lines).strip()


def _strip_code_fences(text: str) -> str:
    stripped = (text or "").strip()
    stripped = re.sub(r"^```[a-zA-Z0-9_-]*\s*", "", stripped)
    stripped = re.sub(r"\s*```$", "", stripped)
    return stripped.strip()


def _is_valid_architecture_prompt(text: str) -> bool:
    normalized = (text or "").strip()
    if not normalized.startswith("架构图提示词模板"):
        return False
    return all(field in normalized for field in ARCHITECTURE_PROMPT_REQUIRED_FIELDS)


def _rewrite_architecture_prompt(text: str, input_text: str) -> str:
    topic = _normalize_inline_text(input_text) or "未提供主题"
    normalized_text = _normalize_inline_text(text)
    diagram_type = _infer_diagram_type(f"{input_text}\n{text}")
    final_prompt = normalized_text or f"请生成一张{diagram_type}，仅展示已明确的系统模块、层次分组和依赖关系，不补充未确认的组件或链路。"
    sections = [
        "架构图提示词模板",
        f"图类型：{diagram_type}",
        f"核心目标：围绕“{topic}”生成一张结构清晰、关系可读、适合继续交给架构图智能体执行的架构图。",
        "节点清单：",
        "- 若输入已明确，请只保留材料中出现的前端、后端、AI 服务、数据源、外部服务等节点。",
        "- 若输入未明确，请保持保守，不补充未知组件。",
        "关系清单：",
        "- 标明调用、转发、读写、检索、生成、认证、同步或异步等已知关系。",
        "- 未确认的关系不要展开。",
        "分层分组：",
        "- 优先按用户层、接入层、应用层、AI 能力层、数据层、外部依赖层分组。",
        "- 如果原始材料已经给出模块边界，以原始材料为准。",
        "布局要求：",
        "- 优先采用从左到右或从上到下的分层布局。",
        "- 突出主链路，减少交叉连线，节点名称保持简洁。",
        "风格要求：",
        "- 使用专业、清晰、偏技术架构展示的表达方式。",
        "- 重点体现结构关系，不追求装饰性元素。",
        "必须包含文案：",
        "- 保留材料中已经明确出现的系统、服务、数据库、接口和外部依赖名称。",
        "禁止事项：",
        "- 不要臆造组件、数据库、接口、调用链或数据流。",
        "- 不要输出代码块、JSON、Mermaid 或额外解释。",
        f"最终出图提示词：{final_prompt}",
    ]
    return "\n".join(sections)


def _infer_diagram_type(text: str) -> str:
    raw = text or ""
    if "部署" in raw:
        return "部署架构图"
    if "业务" in raw:
        return "业务架构图"
    if "技术" in raw:
        return "技术架构图"
    return "系统架构图"


def _normalize_inline_text(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "").strip())


__all__ = ["architecture_prompt_agent", "normalize_architecture_prompt_answer"]
