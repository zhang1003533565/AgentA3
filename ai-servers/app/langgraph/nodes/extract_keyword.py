from app.langgraph.state import ConversationState
from app.multi_agents.leader_agent.agent import leader_agent
from app.multi_agents.md_knowledge_agent.agent import md_knowledge_agent
from app.services.langchain_chat_service import get_chat_service


def extract_keyword_node(state: ConversationState) -> None:
    plan = leader_agent.plan(
        state.input_text,
        state.rag_strategy if state.rag_strategy_explicit else "",
        requested_agent=state.requested_agent or None,
    )
    state.intent = plan.intent
    state.active_agent = plan.target_agent
    if not state.rag_strategy_explicit and plan.rag_strategy:
        state.rag_strategy = plan.rag_strategy
    state.retrieval_meta.update({
        "targetAgent": plan.target_agent,
        "leaderAction": plan.action,
        "routeReason": plan.route_reason,
        "leaderRagStrategy": plan.rag_strategy,
        "leaderDirectAnswer": plan.answer,
        "agentForced": bool(state.requested_agent),
    })
    if plan.intent == "smalltalk":
        state.search_keyword = ""
        state.trace.append({
            "stage": "leader_plan",
            "detail": {"intent": state.intent, "targetAgent": plan.target_agent, "needRetrieval": False},
        })
        return
    if not plan.need_retrieval:
        state.search_keyword = ""
        state.trace.append({
            "stage": "leader_plan",
            "detail": {"intent": state.intent, "targetAgent": plan.target_agent, "needRetrieval": False},
        })
        return
    if plan.intent == "schedule":
        state.search_keyword = "课表查询"
        state.trace.append({
            "stage": "leader_plan",
            "detail": {"intent": state.intent, "targetAgent": plan.target_agent, "needRetrieval": True, "keyword": state.search_keyword},
        })
        return
    chat_service = get_chat_service()
    state.search_keyword = md_knowledge_agent.extract_keyword(state.input_text, chat_service=chat_service)
    state.trace.append({
        "stage": "leader_plan",
        "detail": {"intent": state.intent, "targetAgent": plan.target_agent, "needRetrieval": plan.need_retrieval, "keyword": state.search_keyword},
    })
