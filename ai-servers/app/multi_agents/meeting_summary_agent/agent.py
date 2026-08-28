import logging
from typing import Any, Dict, List, Optional

from app.model_providers.runtime_config import get_active_llm_config
from app.multi_agents.runtime import complete_agent_or_raise

from .tool_binding import (
    MAX_TASK_BLOCK_RETRY,
    TASK_BLOCK_RETRY_HINT,
    format_block_report_for_log,
    has_task_signals,
    inspect_task_blocks,
    process_task_calls,
)

logger = logging.getLogger("multi_agents.meeting_summary_agent")


def _model_label(config: Any) -> str:
    """只取 provider/model 名称用于诊断，绝不输出 api_key 等凭据。"""
    if config is None:
        return "unknown/unknown"
    provider = str(getattr(config, "provider", "") or "").strip() or "unknown"
    model = str(getattr(config, "model", "") or "").strip() or "unknown"
    return f"{provider}/{model}"


def _config_field(config: Any, name: str) -> str:
    return str(getattr(config, name, "") or "unknown")


class MeetingSummaryAgent:
    name = "meeting_summary_agent"

    def process(
        self,
        meeting_content: str,
        evidence: List[Dict[str, Any]],
        chat_service=None,
        authorization: Optional[str] = None,
    ) -> str:
        # runtime 的免费模型链会在候选之间切换，因此"调用方要求的模型"与
        # "真正作答的模型"可能不同，必须分别留痕，否则无法解释任务块偶发丢失。
        requested_config = get_active_llm_config()
        answer = complete_agent_or_raise(self.name, meeting_content, evidence or [], model_provider=chat_service)
        served_config = get_active_llm_config()
        report = inspect_task_blocks(answer)
        logger.info(
            "Agent2 run: stage=first requested=%s served=%s fallback=%s retryCount=0 %s",
            _model_label(requested_config),
            _model_label(served_config),
            _model_label(requested_config) != _model_label(served_config),
            format_block_report_for_log(report),
        )

        if not (report["has_tasks_block"] or report["has_completions_block"]) and not self._should_retry(
            report, meeting_content, authorization
        ):
            logger.warning(
                "Agent2 task block missing: model=%s, provider=%s, retry=false",
                _config_field(served_config, "model"),
                _config_field(served_config, "provider"),
            )

        if self._should_retry(report, meeting_content, authorization):
            logger.warning(
                "Agent2 task block retry: model=%s, provider=%s",
                _config_field(served_config, "model"),
                _config_field(served_config, "provider"),
            )
            retried = complete_agent_or_raise(
                self.name,
                meeting_content + TASK_BLOCK_RETRY_HINT,
                evidence or [],
                model_provider=chat_service,
            )
            retried_report = inspect_task_blocks(retried)
            if retried_report["has_tasks_block"] or retried_report["has_completions_block"]:
                logger.info(
                    "Agent2 task block retry success: served=%s %s",
                    _model_label(served_config),
                    format_block_report_for_log(retried_report),
                )
                # 只执行复检这一份输出，第一次没有块因此不会重复落库
                answer, report = retried, retried_report
            else:
                # 复检仍无块：保留第一次的纪要正文，且绝不继续第二次重试
                logger.warning(
                    "Agent2 task block retry failed: served=%s %s",
                    _model_label(served_config),
                    format_block_report_for_log(retried_report),
                )

        if authorization:
            answer, results = process_task_calls(answer, authorization, meeting_content)
            logger.info(
                "Agent2 task tool executed: final=%s calls=%s ok=%s fail=%s",
                format_block_report_for_log(report),
                len(results),
                sum(1 for item in results if item.get("success")),
                sum(1 for item in results if not item.get("success")),
            )
        return answer

    @staticmethod
    def _should_retry(report: Dict[str, Any], meeting_content: str, authorization: Optional[str]) -> bool:
        """
        复检一次的触发条件（全流程最多一次，由 MAX_TASK_BLOCK_RETRY 与单次调用保证）：

        1. meeting_tasks 与 meeting_task_completions 都不存在
           —— 存在但解析失败属于格式问题，重试不解决，只做告警
        2. 会议转写中确实存在任务分工 / 负责人 / 完成表达等信息
           —— 普通会议不浪费一次模型调用
        3. 必须带有 authorization
           —— 否则工具无法落库，重试没有意义

        安全标准不因重试而降低：重试提示只要求按既有协议补块，
        识别规则、负责人判断、agent-confirm 服务端校验全部不变。
        """
        if MAX_TASK_BLOCK_RETRY < 1:
            return False
        if report["has_tasks_block"] or report["has_completions_block"]:
            return False
        if not authorization:
            return False
        return has_task_signals(meeting_content)


meeting_summary_agent = MeetingSummaryAgent()

__all__ = ["meeting_summary_agent"]
