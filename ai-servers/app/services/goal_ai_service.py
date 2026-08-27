"""学习计划结构化拆解 AI 服务。

负责：
1. 构造用户提示词（目标 + 结构化任务拆解）
2. 调用大模型：Java 透传的 X-AI-* 头优先；缺失的配置项回落到本地
   .env 注入的 LLM_PROVIDER / LLM_BASE_URL / LLM_API_KEY / LLM_MODEL，
   与 PPT 大纲直连审计脚本同一套取值口径
3. 通过 goal_decomposition_agent.parse_goal_payload 守卫为纯 JSON 结果
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, Optional

from fastapi import HTTPException

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
    validate_plan_quality,
)

logger = logging.getLogger(__name__)

_CONFIG_FIELDS = ("provider", "base_url", "model", "api_key")


class GoalDecompositionAIService:
    """目标任务拆解 AI 服务。"""

    def _runtime_config(self, llm_headers: Optional[Dict[str, str]]):
        """组装运行时模型配置：X-AI-* 透传优先，缺失键回落本地 .env 注入的环境变量。"""
        headers = llm_headers or {}
        merged = {
            field: (headers.get(field) or "").strip()
            or os.getenv(f"LLM_{field.upper()}", "").strip()
            for field in _CONFIG_FIELDS
        }
        # provider/model 缺一即无法定位可用端点，视为未配置走统一报错
        if not merged["provider"] or not merged["model"]:
            logger.warning(
                "goal decomposition llm config incomplete (headers=%s), "
                "env fallback keys: %s",
                sorted(headers.keys()) or "none",
                [f"LLM_{field.upper()}" for field in _CONFIG_FIELDS],
            )
            return None
        return build_llm_runtime_config(**merged)

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
            llm_headers: 由 Java 后端透传的 X-AI-* 头；可为空（本地 .env 兜底）

        Returns:
            {"goal": {"title", "description"}, "tasks": [{"task_name", ...}]}
        """
        user_prompt = build_user_prompt(source_type=source_type, content=content)

        config = self._runtime_config(llm_headers)
        token = set_active_llm_config(config) if config is not None else None
        try:
            provider = get_chat_model_provider()
            raw_answer = provider.complete(
                system_prompt=SYSTEM_PROMPT,
                user_prompt=user_prompt,
            )
            result = parse_goal_payload(raw_answer)
            quality_error = validate_plan_quality(result, source_type)
            if quality_error:
                repaired_answer = provider.complete(
                    system_prompt=SYSTEM_PROMPT,
                    user_prompt=(
                        f"{user_prompt}\n\n"
                        "上一次输出未达到学习计划质量要求，请只输出修正后的 JSON。"
                        f"具体问题：{quality_error}\n"
                        "请保留原始目标范围，补齐可执行细分任务、完成标准和一致的预计天数。"
                    ),
                )
                result = parse_goal_payload(repaired_answer)
                quality_error = validate_plan_quality(result, source_type)
                if quality_error:
                    raise HTTPException(status_code=502, detail=f"学习计划拆解质量不足：{quality_error}")
        finally:
            if token is not None:
                reset_active_llm_config(token)

        return result


goal_decomposition_ai_service = GoalDecompositionAIService()
