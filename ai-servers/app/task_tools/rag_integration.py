"""
Meeting Task Tool - Rag Router Integration
=============================================

将 meeting_task_tool 集成到 RAG 路由中。

本文件负责：
1. 添加 meeting_task_tool 的工具实现
2. 在 rag.py 的路由处理中注册该工具
3. 提供 tool function 给 Leader Agent 调用
"""

import sys
from pathlib import Path

# 添加项目根目录到 Python 路径
project_root = Path(__file__).parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

# 导入工具实现
from app.task_tools.meeting_task_tool import (
    meeting_task_tool_handler,
    _get_client,
    MeetingTaskHTTPClient,
    MeetingTaskResult,
)


async def handle_meeting_task_tool(
    action: str,
    authorization: str,
    **kwargs
) -> dict:
    """
    处理 meeting_task_tool 的工具调用
    
    Args:
        action: 操作类型（create_task, list_my_tasks, get_task, update_task_status）
        authorization: Bearer token
        **kwargs: 操作所需的参数
        
    Returns:
        Dict[str, Any]: 工具执行结果
    """
    return await meeting_task_tool_handler(
        action=action,
        authorization=authorization,
        **kwargs
    )


def register_meeting_task_tool():
    """
    注册 meeting_task_tool 到系统
    
    这个方法会在应用启动时被调用。
    它将把 meeting_task_tool 的处理函数暴露给 Leader Agent。
    
    Returns:
        Callable[[str, str, **Dict], Awaitable[Dict]]: 工具处理函数
    """
    # 返回工具处理函数
    return handle_meeting_task_tool


# 测试代码
if __name__ == "__main__":
    import asyncio
    
    async def test_create_task():
        """测试创建任务"""
        client = _get_client()
        
        result = await client.create_task(
            authorization="Bearer test-token",
            meeting_session_id=100,
            assignee_id=1,
            assignee_name="张三",
            title="整理测试数据",
            description="完成会议分配的任务：整理测试数据",
            deadline="2026-08-30T23:59:59Z",
            evidence="张三在会议中明确表示负责整理测试数据"
        )
        
        print(f"Create task result: {result}")
        return result
    
    async def test_list_tasks():
        """测试查询任务列表"""
        client = _get_client()
        
        result = await client.list_my_tasks(authorization="Bearer test-token")
        
        print(f"List tasks result: {result}")
        return result
    
    # 运行测试
    print("Running meeting_task_tool tests...")
    print("=" * 60)
    
    try:
        asyncio.run(test_create_task())
        asyncio.run(test_list_tasks())
    except Exception as e:
        print(f"Test error: {e}")
        print("\nNote: Tests require the Java backend to be running.")
        print("Start AppBackend first, then run these tests again.")
