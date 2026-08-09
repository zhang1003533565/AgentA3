"""AI 架构图生成路由。

暴露 POST /internal/architecture/generate，由 Java 后端 PythonAiProxyService 代理调用。
返回 { title, style, nodes, edges } JSON 结构（不是 Mermaid 文本）。
"""

from typing import List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field

from app.security.internal_auth import require_internal_token
from app.services.architecture_ai_service import architecture_ai_service

router = APIRouter(
    prefix="/internal/architecture",
    tags=["internal-architecture"],
    dependencies=[Depends(require_internal_token)],
)


class ArchitectureGenerateRequest(BaseModel):
    """架构图生成请求。"""

    description: str = Field(..., description="系统需求描述")
    systemType: str = Field(default="", description="系统类型 WEB/APP/MINI_PROGRAM/ADMIN/MICROSERVICE/IOT/AI")
    architectureStyle: str = Field(default="", description="架构模式 AUTO/MONOLITH/FRONT_BACKEND_SEPARATION/MICROSERVICE/CLOUD_NATIVE")
    autoArchitectureLayers: bool = Field(default=True, description="是否由 AI 自动分析架构层级")
    architectureLayers: List[str] = Field(default_factory=list, description="架构层级数组（新版字段）")
    layers: List[str] = Field(default_factory=list, description="架构层级数组")
    focusContents: List[str] = Field(default_factory=list, description="重点展示内容数组（新版字段）")
    displayContent: List[str] = Field(default_factory=list, description="展示内容数组")
    relationMode: str = Field(default="", description="关系表达 AUTO/MODULE/DATA_FLOW/CALL")
    relationType: str = Field(default="", description="关系表达 AUTO/MODULE/DATA_FLOW/CALL_CHAIN/DEPLOYMENT")


class ArchitectureGenerateResponse(BaseModel):
    """架构图生成响应。"""

    title: str
    style: str = ""
    subtitle: str = ""
    layers: List[dict] = Field(default_factory=list)
    thirdParty: List[dict] = Field(default_factory=list)
    features: List[str] = Field(default_factory=list)
    nodes: List[dict] = Field(default_factory=list)
    edges: List[dict] = Field(default_factory=list)
    systemType: str = ""
    autoArchitectureLayers: bool = True
    architectureLayers: List[str] = Field(default_factory=list)
    focusContents: List[str] = Field(default_factory=list)
    requestedRelationMode: str = "AUTO"
    resolvedRelationMode: str = "MODULE"


@router.post("/generate", response_model=ArchitectureGenerateResponse)
def generate_architecture(
    request: ArchitectureGenerateRequest,
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
) -> ArchitectureGenerateResponse:
    """调用大模型生成架构图 JSON。"""
    if not authorization:
        raise HTTPException(status_code=401, detail="未登录或 Token 无效")
    if not request.description or not request.description.strip():
        raise HTTPException(status_code=400, detail="需求描述不能为空")

    llm_headers = {
        "provider": x_ai_provider,
        "base_url": x_ai_base_url,
        "api_key": x_ai_api_key,
        "model": x_ai_model,
    }

    result = architecture_ai_service.generate(
        description=request.description.strip(),
        system_type=request.systemType,
        architecture_style=request.architectureStyle,
        layers=request.architectureLayers or request.layers,
        display_content=request.focusContents or request.displayContent,
        relation_type=request.relationMode or request.relationType,
        auto_architecture_layers=request.autoArchitectureLayers,
        llm_headers=llm_headers,
    )
    return ArchitectureGenerateResponse(**result)
