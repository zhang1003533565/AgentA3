from dataclasses import dataclass
from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


@dataclass(frozen=True)
class MeetingAgent:
    name: str

    def process(self, meeting_content: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(self.name, meeting_content, evidence or [], model_provider=chat_service)


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
