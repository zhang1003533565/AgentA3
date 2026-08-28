"""
Meeting Task Tool - Python AI Server 工具实现
=============================================

实现真正的后端任务 API 调用能力。

本工具实现四个操作：
1. create_task - 创建会议个人任务
2. list_my_tasks - 查询当前用户的任务列表
3. get_task - 查询单个任务详情
4. update_task_status - 更新任务状态

安全规则：
- 所有请求必须通过后端 Java API 的权限控制
- 不要绕过后端的身份认证
- 不要伪造 userId
- 使用项目现有的内部服务认证机制
"""

import httpx
import asyncio
from typing import Dict, Any, Optional, List
from dataclasses import dataclass
import json
import logging

logger = logging.getLogger(__name__)


# =========================
# Configuration
# =========================

# 后端 API 地址配置
JAVA_BACKEND_URL = "http://localhost:8080"
API_PREFIX = "/api/meeting-tasks"


@dataclass
class MeetingTaskResult:
    """统一的任务结果格式"""
    success: bool
    data: Optional[Any] = None
    error: Optional[str] = None
    status_code: Optional[int] = None
    
    def to_dict(self) -> Dict[str, Any]:
        """转换为字典格式"""
        result = {"success": self.success}
        if self.data is not None:
            result["data"] = self.data
        if self.error is not None:
            result["error"] = self.error
        if self.status_code is not None:
            result["statusCode"] = self.status_code
        return result


class MeetingTaskHTTPClient:
    """会议任务 HTTP 客户端"""
    
    def __init__(self, backend_url: str = JAVA_BACKEND_URL):
        self.backend_url = backend_url.rstrip("/")
        self.base_url = f"{self.backend_url}{API_PREFIX}"

        # 创建 HTTP 客户端会话
        self.session: Optional[httpx.AsyncClient] = None
        self._session_loop: Optional[asyncio.AbstractEventLoop] = None

    async def ensure_session(self):
        """确保会话已创建（且绑定当前事件循环）"""
        try:
            current_loop = asyncio.get_running_loop()
        except RuntimeError:
            current_loop = None
        # httpx AsyncClient 绑定创建时的事件循环；跨循环复用会报 "Event loop is closed"。
        # 检测到循环变化时丢弃旧会话，在当前循环上重建。
        if self.session is not None and self._session_loop is not current_loop:
            self.session = None
        if self.session is None or self.session.is_closed:
            timeout = httpx.Timeout(30.0, connect=10.0)
            self.session = httpx.AsyncClient(timeout=timeout, base_url=self.backend_url)
            self._session_loop = current_loop
            
    async def close_session(self):
        """关闭会话"""
        if self.session and not self.session.is_closed:
            await self.session.aclose()
            self.session = None
            
    async def _make_request(
        self,
        method: str,
        endpoint: str,
        authorization: Optional[str] = None,
        json_data: Optional[Dict[str, Any]] = None,
        params: Optional[Dict[str, Any]] = None
    ) -> MeetingTaskResult:
        """
        发起 HTTP 请求到 Java 后端
        
        Args:
            method: HTTP 方法 (GET, POST, PATCH, etc.)
            endpoint: API 端点（相对于/backend_url）
            authorization: Bearer token
            json_data: POST/PUT 请求体
            params: GET 查询参数
            
        Returns:
            MeetingTaskResult: 结构化结果
        """
        await self.ensure_session()
        
        url = f"{self.backend_url}{endpoint}"
        headers = {
            "Content-Type": "application/json",
        }
        
        # 添加认证头
        if authorization:
            headers["Authorization"] = authorization
        
        try:
            if method.upper() == "GET":
                response = await self.session.get(
                    url=url,
                    params=params,
                    headers=headers
                )
            elif method.upper() == "POST":
                response = await self.session.post(
                    url=url,
                    json=json_data,
                    headers=headers
                )
            elif method.upper() == "PATCH":
                response = await self.session.patch(
                    url=url,
                    json=json_data,
                    headers=headers
                )
            else:
                return MeetingTaskResult(
                    success=False,
                    error=f"不支持的 HTTP 方法：{method}",
                    status_code=400
                )
                
            # 解析响应
            try:
                response_data = response.json()
            except json.JSONDecodeError:
                return MeetingTaskResult(
                    success=False,
                    error=f"后端返回非 JSON 数据：{response.text[:500]}",
                    status_code=response.status_code
                )
            
            # 判断 success 字段
            # Java 后端统一返回 {code, msg, data} 格式（code=200 表示成功）；
            # 同时兼容直接返回 {success, data} 的旧格式。
            if "success" in response_data:
                is_success = bool(response_data.get("success"))
            else:
                code = response_data.get("code")
                try:
                    code_value = int(code) if code is not None else None
                except (TypeError, ValueError):
                    code_value = None
                is_success = code_value == 200 or code_value == 0

            if is_success:
                return MeetingTaskResult(
                    success=True,
                    data=response_data.get("data"),
                    status_code=response.status_code
                )
            else:
                # 失败情况
                error_msg = response_data.get("msg") or response_data.get("error") or "未知错误"
                return MeetingTaskResult(
                    success=False,
                    error=error_msg,
                    status_code=response.status_code,
                    data=response_data.get("data")
                )
                
        except httpx.ConnectError as exc:
            logger.error(f"后端连接失败：{exc}")
            return MeetingTaskResult(
                success=False,
                error="后端服务不可用，请检查 AppBackend 是否已启动",
                status_code=503
            )
        except httpx.TimeoutException as exc:
            logger.error(f"后端请求超时：{exc}")
            return MeetingTaskResult(
                success=False,
                error="后端服务响应超时",
                status_code=504
            )
        except Exception as exc:
            logger.error(f"HTTP 请求异常：{exc}")
            return MeetingTaskResult(
                success=False,
                error=f"HTTP 请求异常：{str(exc)}",
                status_code=500
            )
    
    async def create_task(
        self,
        authorization: str,
        meeting_session_id: int,
        assignee_id: int,
        assignee_name: str,
        title: str,
        description: str,
        deadline: str,
        evidence: str
    ) -> MeetingTaskResult:
        """
        创建个人任务
        
        Args:
            authorization: Bearer token
            meeting_session_id: 会议 ID
            assignee_id: 负责人用户 ID
            assignee_name: 负责人名称
            title: 任务标题
            description: 任务描述
            deadline: 截止时间
            evidence: 任务来源依据
            
        Returns:
            MeetingTaskResult: 创建结果
        """
        request_data = {
            "meetingSessionId": meeting_session_id,
            "assigneeId": assignee_id,
            "assigneeName": assignee_name,
            "title": title,
            "description": description,
            "deadline": deadline,
            "evidence": evidence,
            "status": "PENDING"  # 默认状态
        }
        
        return await self._make_request(
            method="POST",
            endpoint=f"{API_PREFIX}",
            authorization=authorization,
            json_data=request_data
        )
    
    async def list_my_tasks(
        self,
        authorization: str,
        meeting_session_id: Optional[int] = None,
        status: Optional[str] = None
    ) -> MeetingTaskResult:
        """
        查询当前用户的任务列表
        
        Args:
            authorization: Bearer token
            meeting_session_id: 会议 ID（可选）
            status: 任务状态（可选）
            
        Returns:
            MeetingTaskResult: 任务列表
        """
        params = {}
        if meeting_session_id is not None:
            params["meetingSessionId"] = meeting_session_id
        if status is not None:
            params["status"] = status
            
        return await self._make_request(
            method="GET",
            endpoint=f"{API_PREFIX}/my",
            authorization=authorization,
            params=params
        )
    
    async def get_task(
        self,
        authorization: str,
        task_id: int
    ) -> MeetingTaskResult:
        """
        查询单个任务详情
        
        Args:
            authorization: Bearer token
            task_id: 任务 ID
            
        Returns:
            MeetingTaskResult: 任务详情
        """
        return await self._make_request(
            method="GET",
            endpoint=f"{API_PREFIX}/{task_id}",
            authorization=authorization
        )
    
    async def update_task_status(
        self,
        authorization: str,
        task_id: int,
        status: str
    ) -> MeetingTaskResult:
        """
        更新任务状态
        
        Args:
            authorization: Bearer token
            task_id: 任务 ID
            status: 新状态
            
        Returns:
            MeetingTaskResult: 更新结果
        """
        request_data = {"status": status}
        
        return await self._make_request(
            method="PATCH",
            endpoint=f"{API_PREFIX}/{task_id}/status",
            authorization=authorization,
            json_data=request_data
        )

    async def agent_confirm_task_completion(
        self,
        authorization: str,
        task_id: int,
        assignee_id: int,
        meeting_session_id: int,
        evidence: str
    ) -> MeetingTaskResult:
        """
        AI 确认任务完成（第五步）

        仅当任务负责人本人在会议中明确表达完成后调用。
        后端会强校验：assigneeId 必须等于任务真实负责人，
        且该负责人是当前会议的真实参会人。

        Args:
            authorization: Bearer token
            task_id: 任务 ID
            assignee_id: 声称确认完成的负责人用户 ID（服务端与任务真实负责人比对）
            meeting_session_id: 当前会议数字 ID
            evidence: 负责人本人明确表达完成的会议原句

        Returns:
            MeetingTaskResult: 确认结果
        """
        request_data = {
            "assigneeId": assignee_id,
            "meetingSessionId": meeting_session_id,
            "evidence": evidence,
        }

        return await self._make_request(
            method="POST",
            endpoint=f"{API_PREFIX}/{task_id}/agent-confirm",
            authorization=authorization,
            json_data=request_data
        )


# =========================
# Tool Functions
# =========================

# 全局 HTTP 客户端实例
_task_client: Optional[MeetingTaskHTTPClient] = None


def _get_client() -> MeetingTaskHTTPClient:
    """获取或创建 HTTP 客户端实例"""
    global _task_client
    if _task_client is None:
        _task_client = MeetingTaskHTTPClient()
    return _task_client


async def meeting_task_tool_handler(
    action: str,
    authorization: Optional[str],
    **kwargs
) -> Dict[str, Any]:
    """
    会议任务工具统一入口函数
    
    支持的操作：
    - create_task: 创建任务
    - list_my_tasks: 查询我的任务
    - get_task: 查询任务详情
    - update_task_status: 更新任务状态
    - confirm_task_completion: AI 确认任务完成（第五步）

    Args:
        action: 操作类型
        authorization: Bearer token（从请求头获取）
        **kwargs: 操作所需的参数

    Returns:
        Dict[str, Any]: 工具执行结果（符合项目规范）
    """
    client = _get_client()

    try:
        if action == "create_task":
            return await _handle_create_task(client, authorization, kwargs)

        elif action == "list_my_tasks":
            return await _handle_list_my_tasks(client, authorization, kwargs)

        elif action == "get_task":
            return await _handle_get_task(client, authorization, kwargs)

        elif action == "update_task_status":
            return await _handle_update_task_status(client, authorization, kwargs)

        elif action == "confirm_task_completion":
            return await _handle_confirm_task_completion(client, authorization, kwargs)

        else:
            return MeetingTaskResult(
                success=False,
                error=f"不支持的操作：{action}",
                status_code=400
            ).to_dict()
            
    except Exception as exc:
        logger.exception(f"工具执行异常：{exc}")
        return MeetingTaskResult(
            success=False,
            error=f"工具执行异常：{str(exc)}",
            status_code=500
        ).to_dict()


async def _handle_create_task(
    client: MeetingTaskHTTPClient,
    authorization: Optional[str],
    params: Dict[str, Any]
) -> Dict[str, Any]:
    """处理 create_task 操作"""
    
    # 验证必要的参数
    required_fields = [
        "meetingSessionId",
        "assigneeId", 
        "assigneeName",
        "title",
        "description",
        "deadline",
        "evidence"
    ]
    
    for field in required_fields:
        if field not in params:
            return MeetingTaskResult(
                success=False,
                error=f"缺少必要参数：{field}",
                status_code=400
            ).to_dict()
    
    # 验证 authorization
    if not authorization:
        return MeetingTaskResult(
            success=False,
            error="缺少授权 token，无法调用后端 API",
            status_code=401
        ).to_dict()
    
    # 调用后端 API
    result = await client.create_task(
        authorization=authorization,
        meeting_session_id=int(params["meetingSessionId"]),
        assignee_id=int(params["assigneeId"]),
        assignee_name=str(params["assigneeName"]),
        title=str(params["title"]),
        description=str(params["description"]),
        deadline=str(params["deadline"]),
        evidence=str(params["evidence"])
    )
    
    return result.to_dict()


async def _handle_list_my_tasks(
    client: MeetingTaskHTTPClient,
    authorization: Optional[str],
    params: Dict[str, Any]
) -> Dict[str, Any]:
    """处理 list_my_tasks 操作"""
    
    # 验证 authorization
    if not authorization:
        return MeetingTaskResult(
            success=False,
            error="缺少授权 token，无法调用后端 API",
            status_code=401
        ).to_dict()
    
    # 调用后端 API
    result = await client.list_my_tasks(
        authorization=authorization,
        meeting_session_id=int(params.get("meetingSessionId")) if params.get("meetingSessionId") else None,
        status=params.get("status")
    )
    
    return result.to_dict()


async def _handle_get_task(
    client: MeetingTaskHTTPClient,
    authorization: Optional[str],
    params: Dict[str, Any]
) -> Dict[str, Any]:
    """处理 get_task 操作"""
    
    # 验证必要的参数
    if "taskId" not in params:
        return MeetingTaskResult(
            success=False,
            error="缺少必要参数：taskId",
            status_code=400
        ).to_dict()
    
    # 验证 authorization
    if not authorization:
        return MeetingTaskResult(
            success=False,
            error="缺少授权 token，无法调用后端 API",
            status_code=401
        ).to_dict()
    
    # 调用后端 API
    result = await client.get_task(
        authorization=authorization,
        task_id=int(params["taskId"])
    )
    
    return result.to_dict()


async def _handle_update_task_status(
    client: MeetingTaskHTTPClient,
    authorization: Optional[str],
    params: Dict[str, Any]
) -> Dict[str, Any]:
    """处理 update_task_status 操作"""

    # 验证必要的参数
    if "taskId" not in params:
        return MeetingTaskResult(
            success=False,
            error="缺少必要参数：taskId",
            status_code=400
        ).to_dict()

    if "status" not in params:
        return MeetingTaskResult(
            success=False,
            error="缺少必要参数：status",
            status_code=400
        ).to_dict()

    # 验证 authorization
    if not authorization:
        return MeetingTaskResult(
            success=False,
            error="缺少授权 token，无法调用后端 API",
            status_code=401
        ).to_dict()

    # 调用后端 API
    result = await client.update_task_status(
        authorization=authorization,
        task_id=int(params["taskId"]),
        status=str(params["status"])
    )

    return result.to_dict()


async def _handle_confirm_task_completion(
    client: MeetingTaskHTTPClient,
    authorization: Optional[str],
    params: Dict[str, Any]
) -> Dict[str, Any]:
    """处理 confirm_task_completion 操作（第五步：AI 确认任务完成）"""

    required_fields = [
        "taskId",
        "assigneeId",
        "meetingSessionId",
        "evidence",
    ]

    for field in required_fields:
        if field not in params:
            return MeetingTaskResult(
                success=False,
                error=f"缺少必要参数：{field}",
                status_code=400
            ).to_dict()

    # 验证 authorization
    if not authorization:
        return MeetingTaskResult(
            success=False,
            error="缺少授权 token，无法调用后端 API",
            status_code=401
        ).to_dict()

    # 调用后端 API（后端会校验 assigneeId 与任务真实负责人一致、
    # 且该负责人是 meetingSessionId 会议的真实参会人）
    result = await client.agent_confirm_task_completion(
        authorization=authorization,
        task_id=int(params["taskId"]),
        assignee_id=int(params["assigneeId"]),
        meeting_session_id=int(params["meetingSessionId"]),
        evidence=str(params["evidence"])
    )

    return result.to_dict()
