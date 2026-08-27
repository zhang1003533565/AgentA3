from typing import Any, Dict, List, Optional

from app.multi_agents.runtime import complete_agent_or_raise

from .tool_binding import process_task_calls


class MeetingSummaryAgent:
    name = "meeting_summary_agent"

    def process(
        self,
        meeting_content: str,
        evidence: List[Dict[str, Any]],
        chat_service=None,
        authorization: Optional[str] = None,
    ) -> str:
        answer = complete_agent_or_raise(self.name, meeting_content, evidence or [], model_provider=chat_service)
        # 第四步：会后纪要中解析出的明确个人任务，经 meeting_task_tool.create_task 落库
        if authorization:
            answer, _task_results = process_task_calls(answer, authorization, meeting_content)
        return answer


meeting_summary_agent = MeetingSummaryAgent()

__all__ = ["meeting_summary_agent"]
