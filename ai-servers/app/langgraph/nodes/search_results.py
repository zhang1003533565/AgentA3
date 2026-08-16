from app.langgraph.state import ConversationState
from app.services.data_store import data_store


def search_results_node(state: ConversationState) -> None:
    if not state.search_keyword:
        state.matched_results = []
        state.retrieval_meta.update({"javaBackendCount": 0, "documentCount": 0})
        state.trace.append({
            "stage": "retrieve",
            "detail": {"skipped": True, "reason": "empty keyword"},
        })
        return
    if state.intent == "schedule":
        state.matched_results = data_store.search_schedule(state.authorization, state.input_text)
    else:
        state.matched_results = data_store.search_keyword(state.authorization, state.search_keyword)
    retrieval_meta = {
        "javaBackendCount": len(state.matched_results),
        "documentCount": 0,
        "localKnowledgeBase": False,
        "localRagStrategies": False,
    }
    state.retrieval_meta.update(retrieval_meta)
    state.trace.append({
        "stage": "retrieve",
        "detail": {
            "intent": state.intent,
            "keyword": state.search_keyword,
            **state.retrieval_meta,
        },
    })
