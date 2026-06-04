import asyncio
from typing import Optional

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.models.schemas import ChatRequest, ChatResponse
from app.model_providers.runtime_config import build_llm_runtime_config, reset_active_llm_config, set_active_llm_config
from app.services.chat_orchestrator import resolve_user_id, run_chat_core
from app.services.langchain_chat_service import get_chat_service
from app.utils.logger import get_logger, mask_id
from app.utils.sse import build_sse, chunk_answer

router = APIRouter(prefix="/internal", tags=["internal-chat"])
logger = get_logger("api.chat")


@router.post("/chat", response_model=ChatResponse)
def internal_chat(
    request: ChatRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
) -> ChatResponse:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")
    user_id = resolve_user_id(x_user_id)
    logger.info(
        "chat request received session_id=%s user_id=%s input_len=%s",
        mask_id(request.sessionId),
        user_id,
        len(request.input or ""),
    )
    token = set_active_llm_config(build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    ))
    try:
        return run_chat_core(request, authorization, user_id)
    finally:
        reset_active_llm_config(token)


@router.post("/chat/stream")
async def internal_chat_stream(
    request: ChatRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
):
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")

    user_id = resolve_user_id(x_user_id)
    logger.info(
        "stream request received session_id=%s user_id=%s input_len=%s",
        mask_id(request.sessionId),
        user_id,
        len(request.input or ""),
    )

    llm_config = build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )

    async def event_stream():
        # Immediately confirm stream is alive to reduce "no response" perception.
        logger.info("stream emit status session_id=%s", mask_id(request.sessionId))
        yield build_sse("status", {"stage": "processing"})
        try:
            token = set_active_llm_config(llm_config)
            try:
                if request.agentName == "meeting_summary_agent":
                    session_id = request.sessionId or ""
                    answer_parts = []
                    yield build_sse("session", {
                        "sessionId": session_id,
                        "sessionToken": "",
                        "model": llm_config.model,
                        "agentName": request.agentName,
                        "answerType": "markdown",
                    })
                    yield build_sse("search", {
                        "searchKeyword": "",
                        "matchedResults": [],
                        "retrievalMeta": {"streamingDirect": True},
                    })
                    service = get_chat_service()
                    for chunk in service.stream_specialist_answer(request.agentName, request.input, []):
                        answer_parts.append(chunk)
                        yield build_sse("delta", {"content": chunk})
                        await asyncio.sleep(0)
                    answer = "".join(answer_parts).strip()
                    yield build_sse("done", {
                        "answer": answer,
                        "answerType": "markdown",
                        "ragStrategy": request.ragStrategy or "naive_rag",
                        "agentName": request.agentName,
                        "searchKeyword": "",
                        "matchedResults": [],
                        "retrievalMeta": {"streamingDirect": True},
                        "trace": [{"stage": "answer", "detail": {"agent": request.agentName, "answerLength": len(answer)}}],
                    })
                    return
                response = await asyncio.to_thread(run_chat_core, request, authorization, user_id)
            finally:
                reset_active_llm_config(token)
            logger.info(
                "stream core completed session_id=%s keyword=%s matched=%s answer_len=%s",
                mask_id(response.sessionId),
                response.searchKeyword,
                len(response.matchedResults or []),
                len(response.answer or ""),
            )
            yield build_sse("session", {
                "sessionId": response.sessionId,
                "sessionToken": response.sessionToken,
                "model": response.model,
                "agentName": response.agentName,
                "answerType": response.answerType,
            })
            yield build_sse("search", {
                "searchKeyword": response.searchKeyword,
                "matchedResults": response.matchedResults,
                "retrievalMeta": response.retrievalMeta,
            })
            for chunk in chunk_answer(response.answer):
                yield build_sse("delta", {"content": chunk})
                await asyncio.sleep(0.035)
            logger.info("stream emit done session_id=%s", mask_id(response.sessionId))
            yield build_sse("done", {
                "answer": response.answer,
                "answerType": response.answerType,
                "ragStrategy": response.ragStrategy,
                "agentName": response.agentName,
                "searchKeyword": response.searchKeyword,
                "matchedResults": response.matchedResults,
                "retrievalMeta": response.retrievalMeta,
                "trace": response.trace,
            })
        except Exception as exc:
            logger.exception("stream failed session_id=%s", mask_id(request.sessionId))
            yield build_sse("error", {"message": str(exc)})

    return StreamingResponse(event_stream(), media_type="text/event-stream")
