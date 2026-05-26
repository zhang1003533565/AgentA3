import os
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
from app.rag.engine import rag_engine
from app.utils.logger import get_logger, mask_id
from app.utils.prompts import DEFAULT_SYSTEM_PROMPT
from app.utils.text_utils import build_session_token

logger = get_logger("langgraph.workflow")


# Graph order: START -> load_memory -> extract_keyword -> search_results -> call_llm -> save_memory -> END
NODE_CHAIN = [
    load_memory_node,
    extract_keyword_node,
    search_results_node,
    call_llm_node,
    save_memory_node,
]


def run_conversation_graph(request: ChatRequest, authorization: str, user_id: Optional[int]) -> ChatResponse:
    deepseek_api_key = os.getenv("DEEPSEEK_API_KEY", "")
    deepseek_model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    if not deepseek_api_key:
        raise HTTPException(status_code=500, detail="未配置 DEEPSEEK_API_KEY")

    session_id = request.sessionId or str(uuid.uuid4())
    session_token = build_session_token(session_id, authorization)
    prompt = request.prompt if request.prompt else DEFAULT_SYSTEM_PROMPT

    requested_strategy = request.ragStrategy or "naive_rag"
    supported_strategies = set(rag_engine.list_strategies())
    active_strategy = requested_strategy if requested_strategy in supported_strategies else "naive_rag"

    state = ConversationState(
        session_id=session_id,
        session_token=session_token,
        authorization=authorization,
        prompt=prompt,
        input_text=request.input,
        model=deepseek_model,
        user_id=user_id,
        rag_strategy=active_strategy,
    )
    if requested_strategy != active_strategy:
        state.retrieval_meta["strategyRequested"] = requested_strategy
        state.retrieval_meta["strategyFallback"] = active_strategy
        state.trace.append({
            "stage": "strategy",
            "detail": {
                "requested": requested_strategy,
                "active": active_strategy,
                "reason": "requested strategy is unknown",
            },
        })

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
        answer=state.answer,
    )
