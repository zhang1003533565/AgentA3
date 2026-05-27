from dataclasses import dataclass
from typing import Any, Dict, List

from app.services.langchain_chat_service import get_chat_service


@dataclass(frozen=True)
class MeetingAgent:
    name: str

    def process(self, meeting_content: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        service = chat_service or get_chat_service()
        return service.generate_specialist_answer(self.name, meeting_content, evidence or [])


meeting_controller_agent = MeetingAgent("meeting_controller_agent")
meeting_transcription_agent = MeetingAgent("meeting_transcription_agent")
meeting_summary_agent = MeetingAgent("meeting_summary_agent")
meeting_member_analysis_agent = MeetingAgent("meeting_member_analysis_agent")
meeting_resource_recommendation_agent = MeetingAgent("meeting_resource_recommendation_agent")
meeting_voice_broadcast_agent = MeetingAgent("meeting_voice_broadcast_agent")

MEETING_AGENTS = {
    agent.name: agent
    for agent in (
        meeting_controller_agent,
        meeting_transcription_agent,
        meeting_summary_agent,
        meeting_member_analysis_agent,
        meeting_resource_recommendation_agent,
        meeting_voice_broadcast_agent,
    )
}
