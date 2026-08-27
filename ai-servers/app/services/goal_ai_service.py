"""学习计划结构化拆解 AI 服务。

负责：
1. 构造用户提示词（目标 + 结构化任务拆解）
2. 调用大模型（复用 Java 透传的 X-AI-* 运行时模型配置）
3. 通过 goal_decomposition_agent.parse_goal_payload 守卫为纯 JSON 结果

与架构图生成服务保持同一套调用范式。
"""

from __future__ import annotations

from typing import Any, Dict, Optional

from app.model_providers.factory import get_chat_model_provider
from app.model_providers.runtime_config import (
    build_llm_runtime_config,
    reset_active_llm_config,
    set_active_llm_config,
)
from app.multi_agents.goal_decomposition_agent.agent import (
    SYSTEM_PROMPT,
    build_user_prompt,
    parse_goal_payload,
)


class GoalDecompositionAIService:
    """目标任务拆解 AI 服务。"""

    def decompose(
        self,
        source_type: str,
        content: str,
        llm_headers: Optional[Dict[str, str]] = None,
    ) -> Dict[str, Any]:
        """调用大模型生成 { goal, tasks } 结构化结果。

        Args:
            source_type: 输入来源类型 text / xlsx / csv
            content: 学习计划文本或表格序列化文本
            llm_headers: 由 Java 后端透传的 X-AI-* 头，用于配置 LLM provider

        Returns:
            {"goal": {"title", "description"}, "tasks": [{"task_name", ...}]}
        """
        user_prompt = build_user_prompt(source_type=source_type, content=content)

        token = None
        if llm_headers:
            token = set_active_llm_config(
                build_llm_runtime_config(
                    provider=llm_headers.get("provider"),
                    base_url=llm_headers.get("base_url"),
                    api_key=llm_headers.get("api_key"),
                    model=llm_headers.get("model"),
                )
            )
        try:
            provider = get_chat_model_provider()
            raw_answer = provider.complete(
                system_prompt=SYSTEM_PROMPT,
                user_prompt=user_prompt,
            )
        finally:
            if token is not None:
                reset_active_llm_config(token)

        return parse_goal_payload(raw_answer)


goal_decomposition_ai_service = GoalDecompositionAIService()
