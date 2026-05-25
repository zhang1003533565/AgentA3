import asyncio
from typing import Optional

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.models.schemas import ChatRequest, ChatResponse
from app.services.chat_orchestrator import resolve_user_id, run_chat_core
from app.utils.logger import get_logger, mask_id
from app.utils.sse import build_sse, chunk_answer

router = APIRouter(prefix="/internal", tags=["internal-chat"])
logger = get_logger("api.chat")


@router.post("/chat", response_model=ChatResponse)
def internal_chat(
    request: ChatRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
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
    return run_chat_core(request, authorization, user_id)


@router.post("/chat/stream")
async def internal_chat_stream(
    request: ChatRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
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

    async def event_stream():
        # Immediately confirm stream is alive to reduce "no response" perception.
        logger.info("stream emit status session_id=%s", mask_id(request.sessionId))
        yield build_sse("status", {"stage": "processing"})
        try:
            response = await asyncio.to_thread(run_chat_core, request, authorization, user_id)
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
                "ragStrategy": response.ragStrategy,
                "searchKeyword": response.searchKeyword,
                "matchedResults": response.matchedResults,
                "retrievalMeta": response.retrievalMeta,
                "trace": response.trace,
            })
        except Exception as exc:
            logger.exception("stream failed session_id=%s", mask_id(request.sessionId))
            yield build_sse("error", {"message": str(exc)})

    return StreamingResponse(event_stream(), media_type="text/event-stream")
