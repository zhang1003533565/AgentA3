from app.langgraph.state import ConversationState
from app.services.data_store import data_store
from app.utils.text_utils import is_schedule_intent


def search_results_node(state: ConversationState) -> None:
    if not state.search_keyword:
        state.matched_results = []
        return
    if is_schedule_intent(state.input_text):
        state.matched_results = data_store.search_schedule(state.authorization, state.input_text)
        return
    state.matched_results = data_store.search_keyword(state.authorization, state.search_keyword)
