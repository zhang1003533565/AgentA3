from app.langgraph.state import ConversationState
from app.multi_agents import memory_agent


def load_memory_node(state: ConversationState) -> None:
    state.history = memory_agent.load(state.session_token)
