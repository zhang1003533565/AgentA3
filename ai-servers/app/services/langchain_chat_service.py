import json
import re
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.model_providers.base import ChatModelProvider
from app.model_providers.deepseek import DeepSeekProvider
from app.model_providers.qwen import QwenProvider
from app.model_providers.qwen.provider import QWEN_PROVIDER_ALIASES
from app.model_providers.runtime_config import require_active_llm_config
from app.model_providers.xiaomi import XiaomiProvider


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
        if agent_name == "ppt_outline_agent":
            answer = _normalize_ppt_outline_answer(answer, input_text)
        if agent_name == "ppt_layout_agent":
            answer = _normalize_ppt_layout_answer(answer, input_text)
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


def build_chat_model_provider(config) -> ChatModelProvider:
    provider = config.normalized_provider()
    if provider in QWEN_PROVIDER_ALIASES:
        return QwenProvider(config=config)
    if provider in {"xiaomi", "mimo", "xiaomi_mimo", "xiaomi-mimo"}:
        return XiaomiProvider(config=config)
    return DeepSeekProvider(config=config)


def get_chat_service() -> LangChainChatService:
    try:
        config = require_active_llm_config()
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    cache_key = config.cache_key()
    if cache_key in chat_services:
        return chat_services[cache_key]
    try:
        service = LangChainChatService(provider=build_chat_model_provider(config))
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
leader_agent, mind_map_agent, textbook_knowledge_agent, textbook_question_single_choice_agent, textbook_question_fill_blank_agent, textbook_question_true_false_agent, textbook_question_multiple_choice_agent, textbook_question_short_answer_agent, textbook_question_calculation_agent, textbook_question_programming_agent, meeting_controller_agent, meeting_transcription_agent, meeting_summary_agent, meeting_member_analysis_agent, meeting_resource_recommendation_agent, meeting_voice_broadcast_agent, ppt_outline_agent, ppt_layout_agent, ppt_review_agent, ppt_image_agent, image_agent。

意图和智能体对应关系：
- 思维导图、脑图：mind_map / mind_map_agent / 需要检索
- Markdown 教材文本、文本知识点提取、教材、课本、章节、考点、知识点：textbook_knowledge / textbook_knowledge_agent / 需要检索
- 选择题、单选题：single_choice / textbook_question_single_choice_agent / 需要检索
- 填空题：fill_blank / textbook_question_fill_blank_agent / 需要检索
- 判断题：true_false / textbook_question_true_false_agent / 需要检索
- 多选题：multiple_choice / textbook_question_multiple_choice_agent / 需要检索
- 简答题：short_answer / textbook_question_short_answer_agent / 需要检索
- 计算题：calculation / textbook_question_calculation_agent / 需要检索
- 编程题、程序题、代码题：programming / textbook_question_programming_agent / 需要检索
- 题库、练习题、出题、试卷但未指定题型：single_choice / textbook_question_single_choice_agent / 需要检索
- 会议总控、会议状态、任务分发、流程调度：meeting_control / meeting_controller_agent / 不需要检索
- 语音转写、会议转写、说话人区分、发言整理：meeting_transcription / meeting_transcription_agent / 不需要检索
- 会议总结、会议纪要、核心观点、主要结论、任务分工、后续计划：meeting_summary / meeting_summary_agent / 不需要检索
- 成员分析、知识薄弱点、理解偏差、参与特征：meeting_member_analysis / meeting_member_analysis_agent / 不需要检索
- 资源推荐、学习资源、推送策略：meeting_resource_recommendation / meeting_resource_recommendation_agent / 不需要检索
- 语音播报、播报脚本、TTS 文案：meeting_voice_broadcast / meeting_voice_broadcast_agent / 不需要检索
- PPT、课件、幻灯片、大纲：ppt_outline / ppt_outline_agent / 需要检索
- PPT 布局、版式、排版：ppt_layout / ppt_layout_agent / 需要检索
- PPT 审查、评分、置信度：ppt_review / ppt_review_agent / 需要检索
- PPT 图片、封面图、页面插图：ppt_image / ppt_image_agent / 需要检索
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
    "textbook_knowledge_agent": """
你是教材知识点智能体。根据用户问题、Markdown 教材文本和检索证据整理教材知识。
要求：
1. 输出标题“## 教材知识点”。
2. 优先基于 evidence 和用户输入中的教材内容，总结定义、关键步骤、公式/概念关系、教学提示。
3. evidence 为空且用户输入没有足够教材内容时，必须明确说明未检索到可引用证据，并给出建议补充的知识库内容，不要编造教材事实。
4. 需要标注证据来源时使用 evidence.source 或 evidence.metadata。
""".strip(),
    "textbook_question_single_choice_agent": """
你是选择题智能体。根据用户输入和证据生成单选题。
要求：
1. 输出标题“## 选择题”。
2. 每题包含题干、A-D 四个选项、唯一正确答案和解析。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足，再生成可安全生成的题目。
""".strip(),
    "textbook_question_fill_blank_agent": """
你是填空题智能体。根据用户输入和证据生成填空题。
要求：
1. 输出标题“## 填空题”。
2. 每题包含题干、标准答案和解析；空缺应考查关键概念、术语或步骤。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足。
""".strip(),
    "textbook_question_true_false_agent": """
你是判断题智能体。根据用户输入和证据生成判断题。
要求：
1. 输出标题“## 判断题”。
2. 每题包含判断陈述、正确/错误答案和解析；避免含混表述。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足。
""".strip(),
    "textbook_question_multiple_choice_agent": """
你是多选题智能体。根据用户输入和证据生成多选题。
要求：
1. 输出标题“## 多选题”。
2. 每题包含题干、A-E 选项、至少两个正确答案和解析。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足。
""".strip(),
    "textbook_question_short_answer_agent": """
你是简答题智能体。根据用户输入和证据生成简答题。
要求：
1. 输出标题“## 简答题”。
2. 每题包含题干、答案要点和评分参考。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足。
""".strip(),
    "textbook_question_calculation_agent": """
你是计算题智能体。根据用户输入和证据生成计算题。
要求：
1. 输出标题“## 计算题”。
2. 每题包含题干、已知条件、解题步骤和最终答案。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足。
""".strip(),
    "textbook_question_programming_agent": """
你是编程题智能体。根据用户输入和证据生成编程题。
要求：
1. 输出标题“## 编程题”。
2. 每题包含题目描述、输入输出、约束、参考思路和测试用例。
3. 题目必须围绕 evidence 或用户明确给出的知识点；证据不足时先说明不足。
""".strip(),
    "meeting_controller_agent": """
你是会议总控智能体。根据会议内容管理会议状态、任务分发和流程调度。
要求：
1. 输出标题“## 会议总控”。
2. 按会议阶段、当前状态、待决事项、任务分发、风险提醒、下一步流程组织内容。
3. 不编造未出现的成员、决定或截止时间；缺失信息要标注“待确认”。
""".strip(),
    "meeting_transcription_agent": """
你是语音转写智能体。根据语音识别文本整理会议转写稿。
要求：
1. 输出标题“## 会议转写”。
2. 尽量区分说话人、整理发言顺序、修正明显断句问题，保留原意。
3. 无法确认说话人时使用“发言人待确认”，不要凭空命名。
""".strip(),
    "meeting_summary_agent": """
你是会议总结智能体。根据会议内容提炼核心观点、主要结论、任务分工和后续计划。
要求：
1. 输出标题“## 会议总结”。
2. 包含会议主题、核心观点、主要结论、任务分工、后续计划、待确认问题。
3. 只基于用户输入和 evidence，不编造会议事实。
""".strip(),
    "meeting_member_analysis_agent": """
你是成员分析智能体。根据会议发言分析成员学习和参与情况。
要求：
1. 输出标题“## 成员分析”。
2. 按成员列出参与特征、可能的知识薄弱点、理解偏差、跟进建议。
3. 结论要基于发言证据；证据不足时明确写“信息不足”。
""".strip(),
    "meeting_resource_recommendation_agent": """
你是资源推荐智能体。根据会议总结和成员分析推荐学习资源与推送策略。
要求：
1. 输出标题“## 资源推荐”。
2. 按成员给出资源类型、推荐理由、学习路径、推送节奏和验收方式。
3. 没有具体资源库时给出资源类型和检索关键词，不编造不存在的链接。
""".strip(),
    "meeting_voice_broadcast_agent": """
你是语音播报智能体。将会议总结、学习建议和推荐内容改写为适合 TTS 播报的自然中文脚本。
要求：
1. 输出标题“## 语音播报稿”。
2. 文案要口语化、短句化，适合直接朗读。
3. 保留关键结论和任务提醒，避免表格、复杂编号和难以播报的符号。
""".strip(),
    "ppt_outline_agent": """
你是 PPT 大纲智能体。根据用户输入和证据生成通用 PPT 大纲。
要求：
1. 输出标题“## PPT 大纲”。
2. 先输出“大纲信息”，包含：主题、使用场景、受众、建议页数、整体目标、风格建议。
3. 每页包含：页标题、页面类型、本页目标、核心内容、展示建议、素材建议。
4. 页面类型优先使用：封面页、目录页、内容页、对比页、流程页、案例页、数据页、总结页、过渡页。
5. 默认 6 页，用户指定页数时按用户要求。
6. 如果用户提供了 `scene_type` 和 `audience`，要分别理解为“使用场景”和“受众”，并在整体目标、风格建议和逐页内容组织中体现。
7. evidence 为空时要明确提示未检索到外部证据。
8. 除非用户明确要求，不要输出课堂互动建议或仅适用于教学课件的措辞。
9. 必须严格使用以下中文字段名，不得替换为其他表达：主题、使用场景、受众、建议页数、整体目标、风格建议、页标题、页面类型、本页目标、核心内容、展示建议、素材建议。
10. 严禁输出这些旧字段或近义旧格式：讲解目标、页面内容建议、课堂互动建议、教学目标、互动建议。
11. “大纲信息”必须出现在所有页面之前，格式固定为 `### 大纲信息`。
12. 每页都要使用如下固定结构，不得缺项，不得增项：
### 第N页
- 页标题：
- 页面类型：
- 本页目标：
- 核心内容：
- 展示建议：
- 素材建议：
13. 如果用户输入偏教学，也优先输出通用 PPT 结构；只有用户明确要求课堂互动、教师讲授设计时，才可以加入教学化表达。
""".strip(),
    "ppt_layout_agent": """
你是 PPT 布局智能体。根据用户输入中的 PPT 大纲或主题，以及 evidence，输出逐页布局方案。
要求：
1. 输出标题“## PPT 布局方案”。
2. 先输出“### 布局信息”，包含：主题、使用场景、受众、整体布局策略、视觉风格建议。
3. 每页包含：页标题、页面类型、布局结构、信息层级、区域安排、视觉建议、素材处理。
4. 输入如果包含 `ppt_outline_agent` 的标准大纲，要优先读取其中的页面类型、本页目标、核心内容、展示建议、素材建议，并据此生成布局。
5. 页面类型优先使用：封面页、目录页、内容页、对比页、流程页、案例页、数据页、总结页、过渡页。
6. 必须严格使用以下中文字段名，不得替换为其他表达：主题、使用场景、受众、整体布局策略、视觉风格建议、页标题、页面类型、布局结构、信息层级、区域安排、视觉建议、素材处理。
7. 严禁输出这些旧字段或近义旧格式：版式类型、标题区、正文区、图表/图片区、视觉层级、留白、讲解动线。
8. “布局信息”必须出现在所有页面之前，格式固定为 `### 布局信息`。
9. 每页都要使用如下固定结构，不得缺项，不得增项：
### 第N页
- 页标题：
- 页面类型：
- 布局结构：
- 信息层级：
- 区域安排：
- 视觉建议：
- 素材处理：
10. 如果用户没有提供大纲，要先基于输入和证据推断最小可用页结构，再给布局。
11. 不生成 PPT 文件，只输出可执行的布局说明。
""".strip(),
    "ppt_review_agent": """
你是 PPT 审查智能体。根据用户输入中的 PPT 大纲、布局或页面内容进行质量审查。
要求：
1. 输出标题“## PPT 审查报告”。
2. 按内容准确性、结构完整性、页面可读性、视觉一致性、教学适配度给出问题清单。
3. 输出“置信度评分：X/100”，并解释扣分原因。
4. 给出按优先级排序的修改建议；证据不足时明确说明无法确认的部分。
""".strip(),
    "ppt_image_agent": """
你是 PPT 图片智能体。根据 PPT 大纲、布局和 evidence 生成封面图、页面插图和示意图提示词。
要求：
1. 输出标题“## PPT 图片提示词”。
2. 按页面列出图片用途、中文提示词、画面元素、构图、风格、比例和避免事项。
3. 不直接生成图片，只输出可交给图像模型的提示词。
4. 如果 evidence 为空，要标注提示词仅基于用户输入。
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


PPT_OUTLINE_REQUIRED_FIELDS = [
    "页标题",
    "页面类型",
    "本页目标",
    "核心内容",
    "展示建议",
    "素材建议",
]

PPT_OUTLINE_FORBIDDEN_FIELDS = [
    "讲解目标",
    "页面内容建议",
    "课堂互动建议",
    "教学目标",
    "互动建议",
]

PPT_LAYOUT_REQUIRED_FIELDS = [
    "页标题",
    "页面类型",
    "布局结构",
    "信息层级",
    "区域安排",
    "视觉建议",
    "素材处理",
]

PPT_LAYOUT_FORBIDDEN_FIELDS = [
    "版式类型",
    "标题区",
    "正文区",
    "图表/图片区",
    "视觉层级",
    "留白",
    "讲解动线",
]

SCENE_TYPE_LABELS = {
    "academic": "学术",
    "business": "商务",
    "roadshow": "路演",
    "report": "述职",
    "teaching": "教学",
}


def _normalize_ppt_outline_answer(text: str, input_text: str = "") -> str:
    answer = _clean_transport_noise(text or "")
    if _is_valid_ppt_outline(answer):
        return answer.strip()
    normalized = _rewrite_ppt_outline(answer, input_text)
    if not _is_valid_ppt_outline(normalized):
        raise HTTPException(status_code=502, detail="ppt_outline_agent 返回内容不符合约定格式，且自动规范化失败")
    return normalized.strip()


def _clean_transport_noise(text: str) -> str:
    cleaned_lines = []
    for line in (text or "").splitlines():
        if "Connection #" in line and "left intact" in line:
            continue
        cleaned_lines.append(line.rstrip())
    return "\n".join(cleaned_lines).strip()


def _is_valid_ppt_outline(text: str) -> bool:
    normalized = (text or "").strip()
    if not normalized.startswith("## PPT 大纲"):
        return False
    if "### 大纲信息" not in normalized:
        return False
    if any(field in normalized for field in PPT_OUTLINE_FORBIDDEN_FIELDS):
        return False
    if "- 使用场景：" not in normalized or "- 受众：" not in normalized:
        return False
    page_blocks = _split_ppt_outline_pages(normalized)
    if not page_blocks:
        return False
    for _, block in page_blocks:
        for field in PPT_OUTLINE_REQUIRED_FIELDS:
            if f"- {field}：" not in block:
                return False
    return True


def _rewrite_ppt_outline(text: str, input_text: str) -> str:
    meta = _extract_outline_meta(input_text, text)
    page_blocks = _split_ppt_outline_pages(text)
    if not page_blocks:
        page_blocks = [("1", text)]
    lines = ["## PPT 大纲", ""]
    if "未检索到外部证据" in text:
        lines.append("**提示：** 未检索到外部证据，以下大纲基于通用信息生成。")
        lines.append("")
    lines.extend([
        "### 大纲信息",
        f"- 主题：{meta['topic']}",
        f"- 使用场景：{meta['scene_label']}",
        f"- 受众：{meta['audience']}",
        f"- 建议页数：{meta['slide_count']} 页",
        f"- 整体目标：{meta['overall_goal']}",
        f"- 风格建议：{meta['style']}",
        "",
    ])
    total_pages = len(page_blocks)
    for index, (page_no, block) in enumerate(page_blocks, start=1):
        title = _extract_page_title(block, page_no)
        goal = _extract_markdown_field(block, ["本页目标", "讲解目标", "教学目标"]) or "概括本页希望传达的关键信息。"
        core_content = _extract_markdown_field(block, ["核心内容", "页面内容建议"]) or "提炼本页的核心要点并控制信息密度。"
        page_type = _infer_page_type(title, goal, core_content, index, total_pages)
        display = _infer_display_suggestion(title, core_content, page_type)
        assets = _infer_asset_suggestion(core_content, page_type)
        lines.extend([
            f"### 第{page_no}页",
            f"- 页标题：{title}",
            f"- 页面类型：{page_type}",
            f"- 本页目标：{_normalize_inline_text(goal)}",
            f"- 核心内容：\n{_format_multiline_bullets(core_content)}",
            f"- 展示建议：{display}",
            f"- 素材建议：{assets}",
            "",
        ])
    return "\n".join(lines).strip()


def _extract_outline_meta(input_text: str, answer_text: str) -> Dict[str, str]:
    raw = input_text or ""
    topic = _match_labeled_value(raw, ["topic", "主题"]) or _extract_first_page_title(answer_text) or "未提供主题"
    scene_type = (_match_labeled_value(raw, ["scene_type", "使用场景"]) or "").strip().lower()
    audience = _match_labeled_value(raw, ["audience", "受众"]) or "未明确"
    slide_count = _match_labeled_value(raw, ["slide_count", "页数"]) or str(max(len(_split_ppt_outline_pages(answer_text)), 1))
    scene_label = SCENE_TYPE_LABELS.get(scene_type, scene_type or "通用")
    overall_goal = _infer_overall_goal(topic, scene_label, audience)
    style = _infer_style(scene_label, audience)
    return {
        "topic": topic,
        "scene_label": scene_label,
        "audience": audience,
        "slide_count": re.sub(r"[^\d]", "", slide_count) or slide_count,
        "overall_goal": overall_goal,
        "style": style,
    }


def _match_labeled_value(text: str, labels: List[str]) -> str:
    for label in labels:
        pattern = rf"{re.escape(label)}\s*[:：]\s*([^;\n]+)"
        match = re.search(pattern, text or "", flags=re.IGNORECASE)
        if match:
            return match.group(1).strip()
    return ""


def _extract_first_page_title(text: str) -> str:
    pages = _split_ppt_outline_pages(text)
    if not pages:
        return ""
    return _extract_page_title(pages[0][1], pages[0][0])


def _split_ppt_outline_pages(text: str) -> List[tuple[str, str]]:
    matches = list(re.finditer(r"###\s*第\s*(\d+)\s*页(?:[:：]\s*([^\n]+))?", text or "", flags=re.IGNORECASE))
    pages: List[tuple[str, str]] = []
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text or "")
        pages.append((match.group(1), (text or "")[start:end].strip()))
    return pages


def _extract_page_title(block: str, page_no: str) -> str:
    title = _extract_markdown_field(block, ["页标题"])
    if title:
        return _normalize_inline_text(title)
    heading_match = re.search(rf"###\s*第\s*{re.escape(page_no)}\s*页[:：]?\s*(.+)", block or "")
    if heading_match and heading_match.group(1).strip():
        return _normalize_inline_text(heading_match.group(1))
    return f"第{page_no}页内容"


def _extract_markdown_field(block: str, names: List[str]) -> str:
    for name in names:
        patterns = [
            rf"-\s*\*\*{re.escape(name)}[:：]\*\*\s*(.*?)(?=\n-\s*\*\*|\n###|\Z)",
            rf"-\s*{re.escape(name)}[:：]\s*(.*?)(?=\n-\s*[^\s]|\n###|\Z)",
        ]
        for pattern in patterns:
            match = re.search(pattern, block or "", flags=re.DOTALL)
            if match:
                return match.group(1).strip()
    return ""


def _normalize_inline_text(text: str) -> str:
    compact = re.sub(r"\s+", " ", (text or "").strip())
    return compact.replace(" ：", "：")


def _format_multiline_bullets(text: str) -> str:
    raw = (text or "").strip()
    if not raw:
        return "  - 提炼本页需要展示的核心要点。"
    items = []
    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        stripped = re.sub(r"^[-*]\s*", "", stripped)
        items.append(f"  - {stripped}")
    if not items:
        items = [f"  - {_normalize_inline_text(raw)}"]
    return "\n".join(items)


def _infer_page_type(title: str, goal: str, content: str, page_index: int, total_pages: int) -> str:
    text = f"{title}\n{goal}\n{content}"
    if page_index == 1 or "封面" in title:
        return "封面页"
    if "目录" in text:
        return "目录页"
    if "总结" in text or "回顾" in text:
        return "总结页"
    if "对比" in text or "vs" in text.lower():
        return "对比页"
    if "流程" in text or "步骤" in text:
        return "流程页"
    if "案例" in text or "场景" in text:
        return "案例页"
    if "数据" in text or "图表" in text:
        return "数据页"
    if "过渡" in text:
        return "过渡页"
    if page_index == total_pages:
        return "总结页"
    return "内容页"


def _infer_display_suggestion(title: str, content: str, page_type: str) -> str:
    text = f"{title}\n{content}"
    if page_type == "封面页":
        return "采用大标题加副标题的封面布局，配合单张主视觉，文字控制在 3 行以内。"
    if "表格" in text or page_type == "对比页":
        return "使用对比表或双栏布局，突出差异点，避免段落堆砌。"
    if "流程图" in text or "步骤" in text or page_type == "流程页":
        return "使用流程图或步骤卡片展示顺序关系，每步保留一句核心说明。"
    if "代码" in text or "伪代码" in text:
        return "采用上文下码或左右分栏布局，代码片段只保留关键逻辑。"
    if "示意图" in text or "应用" in text or page_type == "案例页":
        return "采用左文右图或卡片式布局，用图示辅助说明抽象概念。"
    return "采用要点列表或卡片式布局，每页控制在 3 到 5 个核心信息块。"


def _infer_asset_suggestion(content: str, page_type: str) -> str:
    text = content or ""
    suggestions = []
    if page_type == "封面页":
        suggestions.append("主视觉插图")
    if "表格" in text:
        suggestions.append("对比表格")
    if "图表" in text or page_type == "数据页":
        suggestions.append("数据图表")
    if "流程图" in text or "步骤" in text:
        suggestions.append("流程图")
    if "示意图" in text or "应用" in text:
        suggestions.append("概念示意图")
    if "代码" in text or "伪代码" in text:
        suggestions.append("代码片段")
    if not suggestions:
        suggestions.append("图标或简洁配图")
    return "、".join(dict.fromkeys(suggestions))


def _infer_overall_goal(topic: str, scene_label: str, audience: str) -> str:
    return f"围绕“{topic}”建立清晰的叙事顺序，面向{audience}完成一套适用于{scene_label}场景的 PPT 大纲。"


def _infer_style(scene_label: str, audience: str) -> str:
    if scene_label == "商务":
        return f"表达简洁、结论前置、强调价值与行动建议，适配{audience}阅读。"
    if scene_label == "路演":
        return f"突出亮点、差异化和说服力，控制文字密度，适配{audience}快速浏览。"
    if scene_label == "述职":
        return f"强调成果、问题、计划的递进结构，语气专业克制，适配{audience}。"
    if scene_label == "教学":
        return f"概念清晰、层次递进，但仍保持通用 PPT 表达，适配{audience}。"
    return f"结构清晰、表达中性、信息密度适中，适配{audience}。"


def _normalize_ppt_layout_answer(text: str, input_text: str = "") -> str:
    answer = _clean_transport_noise(text or "")
    if _is_valid_ppt_layout(answer):
        return answer.strip()
    normalized = _rewrite_ppt_layout(answer, input_text)
    if not _is_valid_ppt_layout(normalized):
        raise HTTPException(status_code=502, detail="ppt_layout_agent 返回内容不符合约定格式，且自动规范化失败")
    return normalized.strip()


def _is_valid_ppt_layout(text: str) -> bool:
    normalized = (text or "").strip()
    if not normalized.startswith("## PPT 布局方案"):
        return False
    if "### 布局信息" not in normalized:
        return False
    if any(field in normalized for field in PPT_LAYOUT_FORBIDDEN_FIELDS):
        return False
    if "- 使用场景：" not in normalized or "- 受众：" not in normalized:
        return False
    page_blocks = _split_layout_pages(normalized)
    if not page_blocks:
        return False
    for _, block in page_blocks:
        for field in PPT_LAYOUT_REQUIRED_FIELDS:
            if f"- {field}：" not in block:
                return False
    return True


def _rewrite_ppt_layout(text: str, input_text: str) -> str:
    meta = _extract_outline_meta(input_text, text)
    outline_pages = _extract_outline_pages_from_input(input_text)
    layout_pages = _split_layout_pages(text)
    if not layout_pages and outline_pages:
        layout_pages = [(page["page_no"], page["raw"]) for page in outline_pages]
    if not layout_pages:
        layout_pages = [("1", text)]
    lines = ["## PPT 布局方案", ""]
    lines.extend([
        "### 布局信息",
        f"- 主题：{meta['topic']}",
        f"- 使用场景：{meta['scene_label']}",
        f"- 受众：{meta['audience']}",
        f"- 整体布局策略：{_infer_layout_strategy(meta['scene_label'], meta['audience'])}",
        f"- 视觉风格建议：{_infer_visual_style(meta['scene_label'])}",
        "",
    ])
    total_pages = max(len(layout_pages), len(outline_pages))
    for index, (page_no, block) in enumerate(layout_pages, start=1):
        outline_page = outline_pages[index - 1] if index - 1 < len(outline_pages) else {}
        title = (
            _extract_markdown_field(block, ["页标题"])
            or outline_page.get("title")
            or _extract_page_title(block, page_no)
        )
        page_type = (
            _extract_markdown_field(block, ["页面类型"])
            or outline_page.get("page_type")
            or _infer_page_type(title, outline_page.get("goal", ""), outline_page.get("content", ""), index, total_pages)
        )
        goal = outline_page.get("goal") or _extract_markdown_field(block, ["本页目标", "讲解目标"])
        content = outline_page.get("content") or _extract_markdown_field(block, ["核心内容", "页面内容建议"])
        display = outline_page.get("display") or _extract_markdown_field(block, ["展示建议"])
        assets = outline_page.get("assets") or _extract_markdown_field(block, ["素材建议"])
        layout_structure = _extract_markdown_field(block, ["布局结构", "版式类型"]) or _infer_layout_structure(page_type, display, content)
        info_hierarchy = _extract_markdown_field(block, ["信息层级", "视觉层级"]) or _infer_information_hierarchy(page_type, goal, content)
        region_plan = _extract_region_plan(block) or _infer_region_plan(page_type, content, display)
        visual = _extract_markdown_field(block, ["视觉建议", "留白", "讲解动线"]) or _infer_visual_advice(page_type, display)
        asset_handling = _extract_markdown_field(block, ["素材处理", "图表/图片区"]) or _infer_asset_handling(assets, page_type)
        lines.extend([
            f"### 第{page_no}页",
            f"- 页标题：{_normalize_inline_text(title)}",
            f"- 页面类型：{page_type}",
            f"- 布局结构：{_normalize_inline_text(layout_structure)}",
            f"- 信息层级：{_normalize_inline_text(info_hierarchy)}",
            f"- 区域安排：\n{_format_multiline_bullets(region_plan)}",
            f"- 视觉建议：{_normalize_inline_text(visual)}",
            f"- 素材处理：{_normalize_inline_text(asset_handling)}",
            "",
        ])
    return "\n".join(lines).strip()


def _split_layout_pages(text: str) -> List[tuple[str, str]]:
    matches = list(re.finditer(r"###\s*第\s*(\d+)\s*页(?:[:：]\s*([^\n]+))?", text or "", flags=re.IGNORECASE))
    pages: List[tuple[str, str]] = []
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text or "")
        pages.append((match.group(1), (text or "")[start:end].strip()))
    return pages


def _extract_outline_pages_from_input(text: str) -> List[Dict[str, str]]:
    pages = []
    for page_no, block in _split_ppt_outline_pages(text):
        pages.append({
            "page_no": page_no,
            "raw": block,
            "title": _extract_markdown_field(block, ["页标题"]) or _extract_page_title(block, page_no),
            "page_type": _extract_markdown_field(block, ["页面类型"]),
            "goal": _extract_markdown_field(block, ["本页目标", "讲解目标"]),
            "content": _extract_markdown_field(block, ["核心内容", "页面内容建议"]),
            "display": _extract_markdown_field(block, ["展示建议"]),
            "assets": _extract_markdown_field(block, ["素材建议"]),
        })
    return pages


def _infer_layout_strategy(scene_label: str, audience: str) -> str:
    if scene_label == "商务":
        return f"结论前置，重点页使用强对比层级，适合{audience}快速获取关键信息。"
    if scene_label == "学术":
        return f"按概念到应用递进展开，控制单页信息密度，兼顾图示与定义说明，适合{audience}理解。"
    if scene_label == "路演":
        return f"采用强叙事节奏，重点页突出亮点和差异，适合{audience}快速形成印象。"
    if scene_label == "述职":
        return f"按成果、问题、计划递进布局，突出数据与结论，适合{audience}阅读。"
    return f"保持结构清晰和节奏均衡，兼顾阅读效率与视觉稳定性，适合{audience}。"


def _infer_visual_style(scene_label: str) -> str:
    if scene_label == "学术":
        return "配色简洁克制，以蓝灰或中性色为主，图示强调逻辑关系与可读性。"
    if scene_label == "商务":
        return "使用品牌色点缀重点信息，标题与数字形成明显层级，整体专业干净。"
    if scene_label == "路演":
        return "强调大图和高对比标题，核心数字或亮点采用放大处理，增强记忆点。"
    return "保持字体层级清晰、留白充足、图文比例均衡。"


def _infer_layout_structure(page_type: str, display: str, content: str) -> str:
    text = f"{display}\n{content}"
    if page_type == "封面页":
        return "居中大标题封面布局"
    if page_type == "目录页":
        return "纵向目录导航布局"
    if page_type == "对比页" or "对比" in text or "表格" in text:
        return "左右双栏对比布局"
    if page_type == "流程页" or "流程" in text or "步骤" in text:
        return "横向流程或分步卡片布局"
    if page_type == "数据页" or "图表" in text:
        return "上结论下图表布局"
    if page_type == "总结页":
        return "要点总结加结尾提问布局"
    return "标题加主体内容的单页信息布局"


def _infer_information_hierarchy(page_type: str, goal: str, content: str) -> str:
    if page_type == "封面页":
        return "标题最高，副标题次之，辅助信息最弱。"
    if page_type == "目录页":
        return "章节编号和章节标题为主，次级说明弱化。"
    if page_type == "对比页":
        return "先突出对比维度，再展示差异项，结论最后收束。"
    if page_type == "总结页":
        return "结论最高，其次是回顾要点，最后给出延展问题。"
    if "图表" in (content or ""):
        return "先给一句结论，再展示图表主体，最后补充必要注释。"
    return "标题先行，核心概念居中强化，补充说明放在次级层。"


def _extract_region_plan(block: str) -> str:
    fields = []
    for name in ["区域安排", "标题区", "正文区", "图表/图片区"]:
        value = _extract_markdown_field(block, [name])
        if value:
            fields.append(f"{name}：{_normalize_inline_text(value)}")
    return "\n".join(fields)


def _infer_region_plan(page_type: str, content: str, display: str) -> str:
    if page_type == "封面页":
        return "\n".join([
            "标题区：页面上半区居中放置主标题与副标题。",
            "主视觉区：页面中下部放置一张抽象主题图。",
            "辅助信息区：底部放课程名称或演讲者信息。",
        ])
    if page_type == "目录页":
        return "\n".join([
            "标题区：顶部放目录标题。",
            "导航区：中部纵向排列章节列表或编号导航。",
            "辅助区：底部可放一句总览说明。",
        ])
    if page_type == "对比页":
        return "\n".join([
            "标题区：顶部给出对比主题。",
            "左侧内容区：展示对象 A 的要点或结构。",
            "右侧内容区：展示对象 B 的要点或结构。",
            "结论区：底部收束关键差异。",
        ])
    if "图表" in f"{content}\n{display}" or page_type == "数据页":
        return "\n".join([
            "标题区：顶部给出一句结论型标题。",
            "图表区：中部放主图表或主示意图。",
            "注释区：底部补充关键说明和数据口径。",
        ])
    return "\n".join([
        "标题区：顶部放本页标题。",
        "正文区：中部放 3 到 5 个要点或卡片内容。",
        "辅助区：右侧或底部放图示、图标或补充说明。",
    ])


def _infer_visual_advice(page_type: str, display: str) -> str:
    if page_type == "封面页":
        return "标题字号最大，主视觉占据主要版面，整体留白充足，避免堆叠说明文字。"
    if page_type == "目录页":
        return "目录项保持统一缩进和节奏，可用编号或图标建立导航感。"
    if page_type == "对比页":
        return "两侧内容保持同宽同高，使用统一对齐和相同视觉权重，避免一侧过重。"
    if page_type == "总结页":
        return "结论使用高对比强调，次要说明弱化，末尾问题与结论形成视觉收束。"
    if "动画" in (display or ""):
        return "如果最终渲染支持动效，可用轻量顺序显现，但静态状态下也要保证可读性。"
    return "标题、正文、辅助信息形成三级层级，保证留白和对齐关系稳定。"


def _infer_asset_handling(assets: str, page_type: str) -> str:
    asset_text = _normalize_inline_text(assets)
    if not asset_text or asset_text in {"无特定素材。", "无特定素材", "图标或简洁配图"}:
        if page_type in {"封面页", "内容页", "案例页"}:
            return "可选用简洁示意图或图标辅助说明，避免装饰性素材过多。"
        return "无强制素材要求，优先保证信息结构清晰。"
    return f"优先围绕以下素材组织页面：{asset_text}。素材应服务于信息表达，避免喧宾夺主。"


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
