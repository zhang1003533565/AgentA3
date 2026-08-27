import asyncio
import json
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.models.schemas import CodingAssistRequest
from app.model_providers.runtime_config import build_llm_runtime_config, reset_active_llm_config, set_active_llm_config
from app.multi_agents.runtime import stream_agent
from app.security.internal_auth import require_internal_token
from app.utils.logger import get_logger, mask_id
from app.utils.sse import build_sse

router = APIRouter(
    prefix="/internal/coding",
    tags=["internal-coding"],
    dependencies=[Depends(require_internal_token)],
)
logger = get_logger("api.coding")

AGENT_NAME = "python_coding_tutor_agent"
FALLBACK_SYSTEM_PROMPT = "你是 AI 编程辅导助手，请根据用户输入给出编程辅导。"


def _build_input_text(request: CodingAssistRequest) -> str:
    """把编程辅导上下文组装为 agent 输入的 JSON 文本。"""
    problem = request.problem or {}
    problem_brief = {
        "id": problem.get("id") or problem.get("number"),
        "number": problem.get("number"),
        "title": problem.get("title"),
        "difficulty": problem.get("difficulty"),
        "description": problem.get("description"),
        "examples": problem.get("examples"),
        "tags": problem.get("tags"),
        "funcName": problem.get("funcName"),
    }
    payload: Dict[str, Any] = {
        "questionType": request.questionType,
        "problem": problem_brief,
        "userCode": request.userCode,
        "judgeResult": request.judgeResult,
        "followUp": request.followUp,
        "history": request.history or [],
    }
    return json.dumps(payload, ensure_ascii=False)


def _require_authorization(authorization: Optional[str]) -> None:
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或Token无效")


@router.post("/assist")
def coding_assist(
    request: CodingAssistRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
) -> Dict[str, Any]:
    """非流式编程辅导（供联调/测试使用）。"""
    _require_authorization(authorization)
    llm_config = build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )
    token = set_active_llm_config(llm_config)
    try:
        answer = "".join(stream_agent(AGENT_NAME, _build_input_text(request), []))
    finally:
        reset_active_llm_config(token)
    return {"answer": answer, "agentName": AGENT_NAME, "answerType": "markdown"}


@router.post("/assist/stream")
async def coding_assist_stream(
    request: CodingAssistRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
):
    """流式编程辅导，SSE 事件：status / delta / done / error。"""
    _require_authorization(authorization)
    logger.info(
        "coding assist stream received type=%s problem_id=%s",
        request.questionType,
        (request.problem or {}).get("id"),
    )
    llm_config = build_llm_runtime_config(
        provider=x_ai_provider,
        base_url=x_ai_base_url,
        api_key=x_ai_api_key,
        model=x_ai_model,
    )
    input_text = _build_input_text(request)

    async def event_stream():
        yield build_sse("status", {"stage": "processing"})
        try:
            token = set_active_llm_config(llm_config)
            try:
                answer_parts: List[str] = []
                for chunk in stream_agent(AGENT_NAME, input_text, []):
                    answer_parts.append(chunk)
                    yield build_sse("delta", {"content": chunk})
                    await asyncio.sleep(0)
                answer = "".join(answer_parts).strip()
                yield build_sse("done", {
                    "answer": answer,
                    "answerType": "markdown",
                    "agentName": AGENT_NAME,
                    "trace": [{"stage": "answer", "detail": {"agent": AGENT_NAME, "answerLength": len(answer)}}],
                })
            finally:
                reset_active_llm_config(token)
        except Exception as exc:
            logger.exception("coding assist stream failed")
            yield build_sse("error", {"message": str(exc)})

    return StreamingResponse(event_stream(), media_type="text/event-stream")
