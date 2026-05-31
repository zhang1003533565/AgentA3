import time
import uuid
from typing import Optional

from fastapi import HTTPException

from app.langgraph.nodes import (
    call_llm_node,
    extract_keyword_node,
    load_memory_node,
    save_memory_node,
    search_results_node,
)
from app.langgraph.state import ConversationState
from app.models.schemas import ChatRequest, ChatResponse
from app.model_providers.multimodal import append_image_references_to_text, collect_request_image_references
from app.model_providers.runtime_config import require_active_llm_config
from app.multi_agents.catalog import get_agent_profile, normalize_agent_name
from app.rag.engine import rag_engine
from app.utils.logger import get_logger, mask_id
from app.utils.prompts import DEFAULT_SYSTEM_PROMPT
from app.utils.text_utils import build_session_token

logger = get_logger("langgraph.workflow")


# Graph order: START -> leader memory -> leader route -> textbook retrieval -> specialist answer -> leader memory save -> END
NODE_CHAIN = [
    load_memory_node,
    extract_keyword_node,
    search_results_node,
    call_llm_node,
    save_memory_node,
]


def run_conversation_graph(request: ChatRequest, authorization: str, user_id: Optional[int]) -> ChatResponse:
    try:
        active_llm_config = require_active_llm_config()
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    requested_agent = normalize_agent_name(request.agentName)
    if request.agentName and not requested_agent:
        raise HTTPException(status_code=400, detail="智能体不存在")

    session_id = request.sessionId or str(uuid.uuid4())
    session_token = build_session_token(session_id, authorization)
    prompt = request.prompt if request.prompt else DEFAULT_SYSTEM_PROMPT

    agent_profile = get_agent_profile(requested_agent) if requested_agent else None
    default_strategy = agent_profile["defaultRagStrategy"] if agent_profile else "naive_rag"
    requested_strategy = request.ragStrategy or default_strategy
    supported_strategies = set(rag_engine.list_strategies())
    if requested_strategy not in supported_strategies:
        raise HTTPException(status_code=400, detail=f"未知 RAG 策略：{requested_strategy}")

    state = ConversationState(
        session_id=session_id,
        session_token=session_token,
        authorization=authorization,
        prompt=prompt,
        input_text=append_image_references_to_text(request.input, collect_request_image_references(request)),
        model=active_llm_config.model,
        user_id=user_id,
        rag_strategy=requested_strategy,
        rag_strategy_explicit=bool(request.ragStrategy),
        requested_agent=requested_agent or "",
        active_agent=requested_agent or "leader_agent",
    )

    for node in NODE_CHAIN:
        node_name = getattr(node, "__name__", str(node))
        started = time.perf_counter()
        logger.info(
            "node start name=%s session_id=%s keyword=%s history=%s",
            node_name,
            mask_id(state.session_id),
            state.search_keyword,
            len(state.history or []),
        )
        try:
            node(state)
        except Exception:
            logger.exception("node failed name=%s session_id=%s", node_name, mask_id(state.session_id))
            raise
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        logger.info(
            "node done name=%s session_id=%s elapsed_ms=%s keyword=%s matched=%s answer_len=%s",
            node_name,
            mask_id(state.session_id),
            elapsed_ms,
            state.search_keyword,
            len(state.matched_results or []),
            len(state.answer or ""),
        )

    return ChatResponse(
        sessionId=state.session_id,
        sessionToken=state.session_token,
        model=state.model,
        ragStrategy=state.rag_strategy,
        searchKeyword=state.search_keyword,
        matchedResults=state.matched_results,
        retrievalMeta=state.retrieval_meta,
        trace=state.trace,
        agentName=state.active_agent,
        answer=state.answer,
        answerType=_answer_type_for_agent(state.active_agent),
    )


def _answer_type_for_agent(agent_name: str) -> str:
    mapping = {
        "leader_agent": "text",
        "mind_map_agent": "mermaid_mindmap",
        "textbook_knowledge_agent": "markdown",
        "ppt_outline_agent": "ppt_outline",
        "ppt_layout_agent": "ppt_layout",
        "ppt_review_agent": "ppt_review",
        "ppt_image_agent": "ppt_image_prompt",
        "image_agent": "image_prompt",
    }
    if (agent_name or "").startswith("textbook_question_"):
        return "question_bank"
    if (agent_name or "").startswith("meeting_"):
        return "markdown"
    return mapping.get(agent_name or "", "text")
