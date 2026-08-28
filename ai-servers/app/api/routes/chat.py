import asyncio
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.models.schemas import ChatRequest, ChatResponse
from app.model_providers.runtime_config import build_llm_runtime_config, reset_active_llm_config, set_active_llm_config
from app.multi_agents.runtime import stream_agent
from app.multi_agents.meeting_summary_agent.tool_binding import (
    format_block_report_for_log,
    has_task_signals,
    inspect_task_blocks,
    process_task_calls_async,
    safe_visible_length,
)
from app.observability.langfuse import observe_request, settings_from_headers, use_settings
from app.services.chat_orchestrator import resolve_user_id, run_chat_core
from app.security.internal_auth import require_internal_token
from app.utils.logger import get_logger, mask_id
from app.utils.sse import build_sse, chunk_answer

router = APIRouter(
    prefix="/internal",
    tags=["internal-chat"],
    dependencies=[Depends(require_internal_token)],
)
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
    x_langfuse_enabled: Optional[str] = Header(default=None, alias="X-Langfuse-Enabled"),
    x_langfuse_base_url: Optional[str] = Header(default=None, alias="X-Langfuse-Base-Url"),
    x_langfuse_public_key: Optional[str] = Header(default=None, alias="X-Langfuse-Public-Key"),
    x_langfuse_secret_key: Optional[str] = Header(default=None, alias="X-Langfuse-Secret-Key"),
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
        with use_settings(settings_from_headers(x_langfuse_enabled, x_langfuse_base_url, x_langfuse_public_key, x_langfuse_secret_key)):
            with observe_request(
                "internal.chat",
                session_id=request.sessionId,
                user_id=user_id,
                metadata={"agentName": request.agentName or "leader_agent", "streaming": False},
            ):
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
    x_langfuse_enabled: Optional[str] = Header(default=None, alias="X-Langfuse-Enabled"),
    x_langfuse_base_url: Optional[str] = Header(default=None, alias="X-Langfuse-Base-Url"),
    x_langfuse_public_key: Optional[str] = Header(default=None, alias="X-Langfuse-Public-Key"),
    x_langfuse_secret_key: Optional[str] = Header(default=None, alias="X-Langfuse-Secret-Key"),
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
        settings_scope = use_settings(settings_from_headers(x_langfuse_enabled, x_langfuse_base_url, x_langfuse_public_key, x_langfuse_secret_key))
        settings_scope.__enter__()
        observation_scope = observe_request(
            "internal.chat",
            session_id=request.sessionId,
            user_id=user_id,
            metadata={"agentName": request.agentName or "leader_agent", "streaming": True},
        )
        observation_scope.__enter__()
        try:
            token = set_active_llm_config(llm_config)
            try:
                if request.agentName == "meeting_summary_agent":
                    session_id = request.sessionId or ""
                    answer_parts = []
                    emitted_length = 0
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
                    for chunk in stream_agent(request.agentName, request.input, []):
                        answer_parts.append(chunk)
                        # 抑制内部 meeting_tasks 任务块，避免工具 JSON 外泄给用户
                        visible = safe_visible_length("".join(answer_parts))
                        if visible > emitted_length:
                            yield build_sse("delta", {"content": "".join(answer_parts)[emitted_length:visible]})
                            emitted_length = visible
                        await asyncio.sleep(0)
                    answer = "".join(answer_parts).strip()
                    # 第八步可观测性：流式路径已边生成边推送，不做 retry，仅记录任务块诊断
                    report = inspect_task_blocks(answer)
                    logger.info(
                        "Agent2 run: stage=stream served=%s/%s fallback=false retryCount=0 %s",
                        llm_config.provider,
                        llm_config.model,
                        format_block_report_for_log(report),
                    )
                    if not (report["has_tasks_block"] or report["has_completions_block"]) and has_task_signals(request.input or ""):
                        logger.warning(
                            "Agent2 task block missing: model=%s, provider=%s, retry=false",
                            llm_config.model,
                            llm_config.provider,
                        )
                    # 第四步：会后纪要中的明确个人任务经工具落库，并向用户返回剥离后的干净纪要
                    answer, task_results = await process_task_calls_async(answer, authorization, request.input or "")
                    logger.info(
                        "Agent2 task tool executed: calls=%s ok=%s fail=%s",
                        len(task_results),
                        sum(1 for item in task_results if item.get("success")),
                        sum(1 for item in task_results if not item.get("success")),
                    )
                    yield build_sse("done", {
                        "answer": answer,
                        "answerType": "markdown",
                        "ragStrategy": "direct_agent",
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
        finally:
            observation_scope.__exit__(None, None, None)
            settings_scope.__exit__(None, None, None)

    return StreamingResponse(event_stream(), media_type="text/event-stream")
