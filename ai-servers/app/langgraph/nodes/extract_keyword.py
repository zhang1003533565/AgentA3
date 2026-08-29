from fastapi import HTTPException

from app.langgraph.state import ConversationState
from app.model_providers.factory import get_chat_model_provider
from app.multi_agents.leader_agent.agent import leader_agent


def extract_keyword_node(state: ConversationState) -> None:
    # 显式指定专业智能体时强制路由（与 /internal/chat/stream 的直通行为一致），
    # 避免会议纪要正文中的"会议状态"等字段误触发 Leader 的会议总控/转写关键词规则。
    if state.requested_agent and state.requested_agent != "leader_agent":
        state.intent = "direct_agent"
        state.active_agent = state.requested_agent
        state.search_keyword = ""
        state.retrieval_meta.update({
            "targetAgent": state.requested_agent,
            "leaderAction": "direct_agent",
            "routeReason": "请求显式指定专业智能体，跳过 Leader 规划。",
            "agentForced": True,
        })
        state.trace.append({
            "stage": "leader_plan",
            "detail": {"intent": state.intent, "targetAgent": state.requested_agent, "needRetrieval": False, "agentForced": True},
        })
        return
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
    state.search_keyword = (get_chat_model_provider().extract_search_keyword(state.input_text) or "").strip()
    if not state.search_keyword:
        raise HTTPException(status_code=502, detail="LLM 未返回检索关键词，已禁止本地关键词兜底")
    state.trace.append({
        "stage": "leader_plan",
        "detail": {"intent": state.intent, "targetAgent": plan.target_agent, "needRetrieval": plan.need_retrieval, "keyword": state.search_keyword},
    })
