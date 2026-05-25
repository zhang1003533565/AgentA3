from typing import Dict, List

from app.services.memory_store import memory_store


class MemoryAgent:
    def load(self, session_token: str) -> List[Dict[str, str]]:
        return memory_store.get_history(session_token)

    def save(self, session_token: str, user_input: str, assistant_answer: str) -> None:
        memory_store.append(session_token, user_input, assistant_answer)


memory_agent = MemoryAgent()
