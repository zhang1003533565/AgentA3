from fastapi import HTTPException

from app.langgraph.state import ConversationState
from app.multi_agents.leader_agent.agent import leader_agent
from app.multi_agents.runner import run_specialist_agent


def call_llm_node(state: ConversationState) -> None:
    if state.retrieval_meta.get("leaderAction") == "direct_answer":
        state.answer = state.retrieval_meta.get("leaderDirectAnswer") or ""
        if not state.answer:
            raise HTTPException(status_code=502, detail="Leader LLM 选择直接回答，但 answer 为空，已禁止本地兜底回答")
    elif state.active_agent != "leader_agent":
        state.answer = run_specialist_agent(state.active_agent, state.input_text, state.matched_results)
    else:
        state.answer = leader_agent.answer(
            prompt=state.prompt,
            input_text=state.input_text,
            history=state.history,
            search_keyword=state.search_keyword,
            search_results=state.matched_results,
        )
    state.trace.append({
        "stage": "answer",
        "detail": {
            "agent": state.retrieval_meta.get("targetAgent", "leader_agent"),
            "intent": state.intent,
            "answerLength": len(state.answer or ""),
            "matchedCount": len(state.matched_results or []),
        },
    })
