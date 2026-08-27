"""学习计划结构化拆解路由。

暴露 POST /internal/goal-decomposition/decompose，由 Java 后端代理调用：
上传的学习计划文本/表格序列化文本 -> 专用智能体 -> 纯 JSON { goal, tasks }。

本接口只做拆解，不落库；Goal/Task 持久化由 Java 侧确认入库接口完成。
"""

from typing import List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field

from app.security.internal_auth import require_internal_token
from app.services.goal_ai_service import goal_decomposition_ai_service

router = APIRouter(
    prefix="/internal/goal-decomposition",
    tags=["internal-goal-decomposition"],
    dependencies=[Depends(require_internal_token)],
)


class GoalDecomposeRequest(BaseModel):
    """学习计划拆解请求。"""

    sourceType: str = Field(default="text", description="输入来源类型 text/xlsx/csv")
    content: str = Field(min_length=1, description="学习计划文本或表格序列化文本")


class GoalInfo(BaseModel):
    """拆解出的目标信息。"""

    title: str
    description: str = ""


class SubtaskPlan(BaseModel):
    """拆解出的可执行细分任务。"""

    task_name: str
    description: str = ""
    estimated_days: int = Field(default=1, ge=1)
    order_num: int = Field(ge=1)


class TaskPlan(BaseModel):
    """拆解出的单条任务计划。"""

    task_name: str
    stage: str = ""
    estimated_days: int = Field(default=1, ge=0)
    priority: str = "中"
    order_num: int = Field(ge=1)
    status: str = "pending"
    is_completed: bool = False
    description: str = ""
    subtasks: List[SubtaskPlan] = Field(default_factory=list)


class GoalDecomposeResponse(BaseModel):
    """学习计划拆解响应：纯结构化结果，不含任何解释性文字。"""

    goal: GoalInfo
    tasks: List[TaskPlan] = Field(min_length=1)


@router.post("/decompose", response_model=GoalDecomposeResponse)
def decompose_goal(
    request: GoalDecomposeRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
) -> GoalDecomposeResponse:
    """调用专用智能体，把学习计划内容拆解为目标与任务。"""
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    if not request.content or not request.content.strip():
        raise HTTPException(status_code=400, detail="拆解内容不能为空")

    result = goal_decomposition_ai_service.decompose(
        source_type=request.sourceType,
        content=request.content.strip(),
        llm_headers={
            "provider": x_ai_provider,
            "base_url": x_ai_base_url,
            "api_key": x_ai_api_key,
            "model": x_ai_model,
        },
    )
    return GoalDecomposeResponse(**result)
