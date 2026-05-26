import json
import re
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.deepseek import DeepSeekProvider
from app.model_providers.runtime_config import require_active_llm_config


class LangChainChatService:
    """
    Compatibility facade used by existing nodes/tests.
    Runtime provider implementations now live under app/model_providers.
    """

    def __init__(self, provider: ChatModelProvider) -> None:
        self.provider = provider

    def extract_search_keyword(self, input_text: str) -> str:
        return self.provider.extract_search_keyword(input_text)

    def plan_leader_intent(self, input_text: str, rag_strategy: str = "") -> Dict[str, Any]:
        text = self.provider.complete(
            system_prompt=LEADER_ROUTER_SYSTEM_PROMPT,
            user_prompt=build_leader_router_user_prompt(input_text, rag_strategy),
        )
        parsed = parse_json_object(text)
        if not parsed:
            raise HTTPException(status_code=502, detail=f"Leader LLM 路由结果不是合法 JSON：{text[:300]}")
        return parsed

    def generate_specialist_answer(
        self,
        agent_name: str,
        input_text: str,
        evidence: List[Dict[str, Any]],
    ) -> str:
        system_prompt = SPECIALIST_AGENT_PROMPTS.get(agent_name)
        if not system_prompt:
            raise HTTPException(status_code=400, detail=f"不支持的智能体：{agent_name}")
        text = self.provider.complete(
            system_prompt=system_prompt,
            user_prompt=build_specialist_user_prompt(agent_name, input_text, evidence),
        )
        answer = (text or "").strip()
        if not answer:
            raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空，已禁止本地模板兜底")
        if agent_name == "mind_map_agent":
            answer = _sanitize_mind_map_answer(answer)
        return answer

    def answer(
        self,
        prompt: str,
        input_text: str,
        history: List[Dict[str, str]],
        search_keyword: str,
        search_results: List[Dict[str, Any]],
    ) -> str:
        return self.provider.answer(
            prompt=prompt,
            input_text=input_text,
            history=history,
            search_keyword=search_keyword,
            search_results=search_results,
        )


chat_services: Dict[tuple[str, str, str, str], LangChainChatService] = {}


def get_chat_service() -> LangChainChatService:
    try:
        config = require_active_llm_config()
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    cache_key = config.cache_key()
    if cache_key in chat_services:
        return chat_services[cache_key]
    try:
        service = LangChainChatService(provider=DeepSeekProvider(config=config))
        chat_services[cache_key] = service
        return service
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))


LEADER_ROUTER_SYSTEM_PROMPT = """
你是智慧校园 AI 的 Leader 智能体，只负责意图识别、路由决策和必要的直接回复。
请根据用户输入，从以下动作中选择一个：
1. direct_answer：问候、感谢、告别等普通闲聊，Leader 直接回复。
2. call_tool：需要调用接口或工具。课表/课程安排使用 java_schedule_api；统计、列表、优惠券、食堂、档口、菜品等结构化查询使用 text_to_sql。
3. delegate_agent：交给专业智能体。

专业智能体只能从这些值选择：
leader_agent, mind_map_agent, md_knowledge_agent, textbook_knowledge_agent, textbook_question_bank_agent, ppt_agent, image_agent。

意图和智能体对应关系：
- 思维导图、脑图：mind_map / mind_map_agent / 需要检索
- Markdown 或文本知识点提取：md_knowledge / md_knowledge_agent / 不需要检索
- 教材、课本、章节、考点、知识点：textbook_knowledge / textbook_knowledge_agent / 需要检索
- 题库、练习题、出题、试卷：question_bank / textbook_question_bank_agent / 需要检索
- PPT、课件、幻灯片：ppt / ppt_agent / 需要检索
- 图片、配图、插图、封面图：image / image_agent / 需要检索
- 未明确命中特定生成类任务：campus_search / textbook_knowledge_agent / 需要检索

只输出 JSON，不要输出 Markdown，不要解释。JSON 字段：
intent, target_agent, need_retrieval, rag_strategy, action, tool_name, route_reason, answer。
rag_strategy 仅在 need_retrieval=true 时填写；direct_answer 的 answer 必须是自然中文回复。
如果无法判断，仍然要在 JSON 的 route_reason 中写明不确定原因，不允许输出非 JSON 文本。
""".strip()


def build_leader_router_user_prompt(input_text: str, rag_strategy: str) -> str:
    return json.dumps({
        "user_input": input_text or "",
        "requested_rag_strategy": rag_strategy or "",
        "allowed_rag_strategy_when_needed": rag_strategy or "按目标智能体默认策略",
    }, ensure_ascii=False)


SPECIALIST_AGENT_PROMPTS: Dict[str, str] = {
    "mind_map_agent": """
你是思维导图智能体。根据用户输入和证据输出 Mermaid mindmap Markdown。
要求：
1. 输出一个可渲染的 ```mermaid mindmap。
2. 节点层级清楚，适合课堂复习。
3. 无论证据是否为空，都只能输出 Mermaid 代码块本身，禁止输出任何额外说明、前后缀、标题、注释。
4. 不要编造具体教材出处。
""".strip(),
    "md_knowledge_agent": """
你是 Markdown 知识点提取智能体。根据用户输入提取知识点，输出中文 Markdown。
要求：
1. 输出标题“## Markdown 知识点提取”。
2. 用项目符号列出概念、定义、关系、易错点。
3. 如果输入不足，明确说明缺少哪些内容；禁止使用本地模板兜底。
""".strip(),
    "textbook_knowledge_agent": """
你是教材知识点智能体。根据用户问题和检索证据整理教材知识。
要求：
1. 输出标题“## 教材知识点”。
2. 优先基于 evidence，总结定义、关键步骤、公式/概念关系、教学提示。
3. evidence 为空时，必须明确说明未检索到可引用证据，并给出建议补充的知识库内容，不要编造教材事实。
4. 需要标注证据来源时使用 evidence.source 或 evidence.metadata。
""".strip(),
    "textbook_question_bank_agent": """
你是教材题库智能体。根据用户输入和证据生成练习题。
要求：
1. 输出标题“## 教材题库”。
2. 生成选择题、判断题、简答题的组合，并附参考答案。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足，再生成可安全生成的题目。
""".strip(),
    "ppt_agent": """
你是 PPT 智能体。根据用户输入和证据生成课件大纲。
要求：
1. 输出标题“## PPT 大纲”。
2. 每页包含页标题、讲解目标、页面内容建议、课堂互动建议。
3. 默认 6 页，用户指定页数时按用户要求。
4. evidence 为空时要明确提示未检索到外部证据。
""".strip(),
    "image_agent": """
你是图片智能体。根据课程主题和证据生成教学配图提示词。
要求：
1. 输出标题“## 图片智能体提示词”。
2. 给出中文提示词、画面元素、构图、风格、避免事项。
3. 不直接生成图片，只给可交给图像模型的提示词。
4. 如果 evidence 为空，要标注提示词仅基于用户输入。
""".strip(),
}


def build_specialist_user_prompt(agent_name: str, input_text: str, evidence: List[Dict[str, Any]]) -> str:
    return json.dumps({
        "agent_name": agent_name,
        "user_input": input_text or "",
        "evidence": normalize_evidence(evidence),
        "failure_policy": "如果模型无法完成，请直接说明缺少什么信息或配置；不要输出本地兜底模板。",
    }, ensure_ascii=False, indent=2)


def normalize_evidence(evidence: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    normalized: List[Dict[str, Any]] = []
    for item in evidence or []:
        if not isinstance(item, dict):
            continue
        normalized.append({
            "id": item.get("id"),
            "source": item.get("source") or item.get("name") or item.get("type"),
            "content": str(item.get("content") or item.get("name") or "")[:1200],
            "score": item.get("score"),
            "metadata": item.get("metadata") if isinstance(item.get("metadata"), dict) else {},
        })
        if len(normalized) >= 8:
            break
    return normalized


def _sanitize_mind_map_answer(text: str) -> str:
    match = re.search(r"```mermaid\s*([\s\S]*?)```", text or "", flags=re.IGNORECASE)
    if not match:
        raise HTTPException(status_code=502, detail="mind_map_agent 必须返回 Mermaid 代码块，当前返回不符合约束")
    code = (match.group(1) or "").strip()
    if not code:
        raise HTTPException(status_code=502, detail="mind_map_agent 返回了空 Mermaid 代码块")
    if not re.search(r"(^|\n)\s*mindmap\b", code, flags=re.IGNORECASE):
        raise HTTPException(status_code=502, detail="mind_map_agent 返回的 Mermaid 代码块缺少 mindmap 声明")
    return f"```mermaid\n{code}\n```"


def parse_json_object(text: str) -> Dict[str, Any]:
    raw = (text or "").strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?", "", raw, flags=re.IGNORECASE).strip()
        raw = re.sub(r"```$", "", raw).strip()
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        match = re.search(r"\{.*\}", raw, flags=re.DOTALL)
        if not match:
            return {}
        try:
            parsed = json.loads(match.group(0))
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            return {}
