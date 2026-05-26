from app.langgraph.state import ConversationState
from app.multi_agents.leader_agent.agent import leader_agent


def load_memory_node(state: ConversationState) -> None:
    state.history = leader_agent.load_memory(state.session_token)
