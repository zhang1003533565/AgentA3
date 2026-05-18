from app.langgraph.state import ConversationState
from app.services.memory_store import memory_store


def load_memory_node(state: ConversationState) -> None:
    state.history = memory_store.get_history(state.session_token)
