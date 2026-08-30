"""
Meeting Task Tool - 测试脚本
==========================

本脚本用于测试 meeting_task_tool 的实际功能。

前提条件：
1. AppBackend Java 后端已启动（默认端口 8080）
2. Python AI Server 环境已激活
3. 用户已登录（获取有效的 JWT token）

使用方法：
    python ai-servers/app/task_tools/test_meeting_task_tool.py
"""

import asyncio
import sys
from pathlib import Path

# 添加项目根目录到路径
project_root = Path(__file__).parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

from app.task_tools.meeting_task_tool import (
    MeetingTaskHTTPClient,
    MeetingTaskResult,
)


async def test_create_task():
    """测试 1: 创建任务"""
    print("\n" + "="*60)
    print("测试 1: create_task")
    print("="*60)
    
    client = MeetingTaskHTTPClient()
    
    # 使用测试数据
    result = await client.create_task(
        authorization="Bearer test-token-from-logging",  # 实际应使用真实 Token
        meeting_session_id=100,
        assignee_id=1,
        assignee_name="张三",
        title="整理测试数据",
        description="完成会议分配的任务：整理测试数据",
        deadline="2026-08-30T23:59:59Z",
        evidence="张三在会议中明确表示负责整理测试数据"
    )
    
    print(f"\n结果：{result.to_dict()}")
    
    if result.success:
        print("✓ 创建任务成功")
        return True
    else:
        print(f"✗ 创建任务失败：{result.error}")
        return False


async def test_list_my_tasks():
    """测试 2: 查询我的任务列表"""
    print("\n" + "="*60)
    print("测试 2: list_my_tasks")
    print("="*60)
    
    client = MeetingTaskHTTPClient()
    
    result = await client.list_my_tasks(
        authorization="Bearer test-token-from-logging"
    )
    
    print(f"\n结果：{result.to_dict()}")
    
    if result.success:
        data = result.data or []
        if isinstance(data, list):
            count = len(data)
            if count > 0:
                print(f"✓ 找到 {count} 个任务")
            else:
                print("✓ 没有历史任务（正常）")
        return True
    else:
        print(f"✗ 查询任务失败：{result.error}")
        return False


async def test_get_task():
    """测试 3: 查询任务详情"""
    print("\n" + "="*60)
    print("测试 3: get_task")
    print("="*60)
    
    client = MeetingTaskHTTPClient()
    
    # 使用测试任务 ID
    task_id = 1
    
    result = await client.get_task(
        authorization="Bearer test-token-from-logging",
        task_id=task_id
    )
    
    print(f"\n结果：{result.to_dict()}")
    
    if result.success:
        print(f"✓ 查询任务 {task_id} 成功")
        return True
    else:
        print(f"✗ 查询任务 {task_id} 失败：{result.error}")
        # 如果是因为不存在，不算错误
        if "not found" in result.error.lower() or "不存在" in result.error:
            print("(任务不存在，属于正常情况)")
            return True
        return False


async def test_update_task_status():
    """测试 4: 更新任务状态"""
    print("\n" + "="*60)
    print("测试 4: update_task_status")
    print("="*60)
    
    client = MeetingTaskHTTPClient()
    
    # 使用测试任务 ID
    task_id = 1
    
    result = await client.update_task_status(
        authorization="Bearer test-token-from-logging",
        task_id=task_id,
        status="COMPLETED"
    )
    
    print(f"\n结果：{result.to_dict()}")
    
    if result.success:
        print(f"✓ 更新任务 {task_id} 状态成功")
        return True
    else:
        print(f"✗ 更新任务 {task_id} 状态失败：{result.error}")
        
        # 权限错误算正常
        if "403" in str(result.status_code) or "无权限" in result.error:
            print("(权限拒绝，属于正常保护机制)")
            return True
        return False


async def test_backend_not_running():
    """测试 5: 后端未启动的情况"""
    print("\n" + "="*60)
    print("测试 5: 后端服务不可用")
    print("="*60)
    
    # 使用一个不可能有后端的地址
    client = MeetingTaskHTTPClient(backend_url="http://localhost:99999")
    
    result = await client.create_task(
        authorization="Bearer test-token",
        meeting_session_id=100,
        assignee_id=1,
        assignee_name="张三",
        title="测试任务",
        description="测试描述",
        deadline="2026-08-30T23:59:59Z",
        evidence="测试依据"
    )
    
    print(f"\n结果：{result.to_dict()}")
    
    if not result.success and result.status_code == 503:
        print("✓ 正确返回后端不可用的错误")
        return True
    else:
        print("✗ 应该返回后端不可用错误")
        return False


async def main():
    """运行所有测试"""
    print("\n" + "="*60)
    print("Meeting Task Tool - 功能测试")
    print("="*60)
    print("\n注意:")
    print("1. create_token 需要一个真实的 JWT token")
    print("2. 请确保 AppBackend 已启动")
    print("3. 如果没有真实 token，可以使用以下步骤:")
    print("   a) 登录系统获取 token")
    print("   b) 替换下面的 'Bearer test-token-from-logging'")
    print("="*60)
    
    results = {}
    
    # 测试 1: create_task
    results["create_task"] = await test_create_task()
    
    # 测试 2: list_my_tasks
    results["list_my_tasks"] = await test_list_my_tasks()
    
    # 测试 3: get_task
    results["get_task"] = await test_get_task()
    
    # 测试 4: update_task_status
    results["update_task_status"] = await test_update_task_status()
    
    # 测试 5: 后端未启动
    results["backend_not_running"] = await test_backend_not_running()
    
    # 总结
    print("\n" + "="*60)
    print("测试结果汇总")
    print("="*60)
    
    passed = sum(1 for v in results.values() if v)
    total = len(results)
    
    for name, success in results.items():
        status = "✓ 通过" if success else "✗ 失败"
        print(f"{name}: {status}")
    
    print(f"\n总计：{passed}/{total} 测试通过")
    
    if passed == total:
        print("\n🎉 所有测试通过！")
        return 0
    else:
        print(f"\n⚠️ {total - passed} 个测试失败")
        print("\n可能的原因:")
        print("1. AppBackend 未启动")
        print("2. JWT token 无效或过期")
        print("3. 网络连接问题")
        print("4. 数据库中没有测试数据")
        return 1


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    sys.exit(exit_code)
