from app.langgraph.state import ConversationState
from app.services.langchain_chat_service import get_chat_service
from app.utils.text_utils import is_schedule_intent, is_smalltalk_intent


def extract_keyword_node(state: ConversationState) -> None:
    if is_smalltalk_intent(state.input_text):
        state.search_keyword = ""
        return
    if is_schedule_intent(state.input_text):
        state.search_keyword = "课表查询"
        return
    state.search_keyword = get_chat_service().extract_search_keyword(state.input_text)
