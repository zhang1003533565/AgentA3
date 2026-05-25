from app.langgraph.state import ConversationState
from app.multi_agents import planner_agent
from app.services.langchain_chat_service import get_chat_service


def extract_keyword_node(state: ConversationState) -> None:
    decision = planner_agent.decide(state.input_text)
    state.intent = decision.intent
    if decision.intent == "smalltalk":
        state.search_keyword = ""
        return
    if decision.intent == "schedule":
        state.search_keyword = "课表查询"
        return
    state.search_keyword = get_chat_service().extract_search_keyword(state.input_text)
