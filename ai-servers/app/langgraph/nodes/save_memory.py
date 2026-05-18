from app.langgraph.state import ConversationState
from app.services.memory_store import memory_store


def save_memory_node(state: ConversationState) -> None:
    memory_store.append(state.session_token, state.input_text, state.answer)
