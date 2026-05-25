from app.langgraph.state import ConversationState
from app.multi_agents.memory_agent.agent import memory_agent


def save_memory_node(state: ConversationState) -> None:
    memory_agent.save(state.session_token, state.input_text, state.answer)
