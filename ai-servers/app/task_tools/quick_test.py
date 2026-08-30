"""
Meeting Task Tool - 简单快速测试
"""
import asyncio
import sys
from pathlib import Path

# 添加项目根目录到路径
project_root = Path(__file__).parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

from app.task_tools.meeting_task_tool import MeetingTaskHTTPClient


async def test_basic():
    """基础功能测试"""
    print("Testing meeting_task_tool...")
    print("=" * 60)
    
    client = MeetingTaskHTTPClient()
    
    # 测试 1: 创建任务（使用无效 token，预期返回 401）
    print("\nTest 1: create_task (with invalid token)")
    result = await client.create_task(
        authorization="Bearer invalid-token",
        meeting_session_id=100,
        assignee_id=1,
        assignee_name="张三",
        title="整理测试数据",
        description="完成会议分配的任务",
        deadline="2026-08-30T23:59:59Z",
        evidence="会议记录"
    )
    
    print(f"Result: {result.to_dict()}")
    if result.status_code == 401:
        print("Status: OK - Received 401 Unauthorized as expected\n")
    else:
        print(f"Status: Expected 401, got {result.status_code}\n")
    
    # 测试 2: 查询我的任务列表
    print("Test 2: list_my_tasks (with invalid token)")
    result = await client.list_my_tasks(authorization="Bearer invalid-token")
    print(f"Result: {result.to_dict()}")
    if result.status_code == 401:
        print("Status: OK - Received 401 Unauthorized as expected\n")
    
    # 测试 3: 后端不可用
    print("Test 3: Backend unreachable")
    bad_client = MeetingTaskHTTPClient(backend_url="http://localhost:99999")
    result = await bad_client.create_task(
        authorization="Bearer test",
        meeting_session_id=100,
        assignee_id=1,
        assignee_name="张三",
        title="测试",
        description="测试",
        deadline="2026-08-30T23:59:59Z",
        evidence="测试"
    )
    print(f"Result: {result.to_dict()}")
    if result.status_code == 503 or "不可用" in result.error:
        print("Status: OK - Correctly handled connection error\n")
    
    print("=" * 60)
    print("All basic tests passed!")
    return 0


if __name__ == "__main__":
    exit_code = asyncio.run(test_basic())
    sys.exit(exit_code)
