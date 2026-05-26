from app.langgraph.state import ConversationState
from app.multi_agents.answer_agent.agent import answer_agent
from app.multi_agents.critic_agent.agent import critic_agent
from app.services.langchain_chat_service import get_chat_service


def call_llm_node(state: ConversationState) -> None:
    draft = answer_agent.answer(
        prompt=state.prompt,
        input_text=state.input_text,
        history=state.history,
        search_keyword=state.search_keyword,
        search_results=state.matched_results,
        chat_service=get_chat_service(),
    )
    state.answer = critic_agent.refine(draft)
    state.trace.append({
        "stage": "answer",
        "detail": {
            "answerLength": len(state.answer or ""),
            "matchedCount": len(state.matched_results or []),
        },
    })
