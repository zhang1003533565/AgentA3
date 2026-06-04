from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise


class MeetingVoiceBroadcastAgent:
    name = "meeting_voice_broadcast_agent"

    def process(self, meeting_content: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return complete_agent_or_raise(self.name, meeting_content, evidence or [], model_provider=chat_service)


meeting_voice_broadcast_agent = MeetingVoiceBroadcastAgent()

__all__ = ["meeting_voice_broadcast_agent"]
