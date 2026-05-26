from app.langgraph.state import ConversationState
from app.multi_agents.leader_agent.agent import leader_agent


def save_memory_node(state: ConversationState) -> None:
    leader_agent.save_memory(state.session_token, state.input_text, state.answer)
