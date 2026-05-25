from app.langgraph.state import ConversationState
from app.multi_agents import retriever_agent


def search_results_node(state: ConversationState) -> None:
    if not state.search_keyword:
        state.matched_results = []
        return
    state.matched_results = retriever_agent.retrieve(
        authorization=state.authorization,
        intent=state.intent,
        keyword=state.search_keyword,
        input_text=state.input_text,
    )
