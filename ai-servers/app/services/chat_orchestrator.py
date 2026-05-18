from typing import Optional

from app.langgraph.graph.workflow import run_conversation_graph
from app.models.schemas import ChatRequest, ChatResponse


def resolve_user_id(x_user_id: Optional[str]) -> Optional[int]:
    if not x_user_id:
        return None
    try:
        return int(x_user_id)
    except Exception:
        return None


def run_chat_core(request: ChatRequest, authorization: str, user_id: Optional[int]) -> ChatResponse:
    return run_conversation_graph(request=request, authorization=authorization, user_id=user_id)
