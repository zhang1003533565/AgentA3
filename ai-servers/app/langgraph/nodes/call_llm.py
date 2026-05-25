from app.langgraph.state import ConversationState
from app.multi_agents import critic_agent
from app.services.langchain_chat_service import get_chat_service


def call_llm_node(state: ConversationState) -> None:
    draft = get_chat_service().answer(
        prompt=state.prompt,
        input_text=state.input_text,
        history=state.history,
        search_keyword=state.search_keyword,
        search_results=state.matched_results,
    )
    state.answer = critic_agent.refine(draft)
