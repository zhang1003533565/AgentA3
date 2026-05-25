from app.langgraph.state import ConversationState
from app.multi_agents.retriever_agent.agent import retriever_agent


def search_results_node(state: ConversationState) -> None:
    if not state.search_keyword:
        state.matched_results = []
        state.retrieval_meta.update({"javaBackendCount": 0, "documentCount": 0})
        state.trace.append({
            "stage": "retrieve",
            "detail": {"skipped": True, "reason": "empty keyword"},
        })
        return
    state.matched_results, retrieval_meta = retriever_agent.retrieve_with_meta(
        authorization=state.authorization,
        intent=state.intent,
        keyword=state.search_keyword,
        input_text=state.input_text,
        rag_strategy=state.rag_strategy,
    )
    state.retrieval_meta.update(retrieval_meta)
    state.trace.append({
        "stage": "retrieve",
        "detail": {
            "intent": state.intent,
            "keyword": state.search_keyword,
            **state.retrieval_meta,
        },
    })
