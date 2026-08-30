import unittest
from unittest.mock import patch

from app.services.ai_ppt_generation_tool_service import start_leader_ppt_generation


class AiPptGenerationToolServiceTest(unittest.TestCase):
    @patch("app.services.ai_ppt_generation_tool_service.ppt_generation_service.create_leader_pipeline_task")
    def test_start_leader_ppt_generation_returns_task_payload(self, create_task):
        create_task.return_value = {
            "taskId": "ppt_task_0123456789abcdef0123456789abcdef",
            "status": "queued",
            "kind": "leader_ppt_pipeline",
            "message": "queued",
            "pollPath": "/api/app/ai/ppt/tasks/ppt_task_0123456789abcdef0123456789abcdef",
            "streamPath": "/api/app/ai/ppt/tasks/ppt_task_0123456789abcdef0123456789abcdef/stream",
        }
        payload = start_leader_ppt_generation(
            "操作系统进程调度知识点课件",
            object(),
            {"userId": "1001"},
        )
        self.assertEqual("ppt_task_0123456789abcdef0123456789abcdef", payload["taskId"])
        self.assertEqual("操作系统进程调度知识点课件", payload["title"])
        self.assertEqual("ppt_outline_agent", payload["boundAgent"])
        create_task.assert_called_once()
        request = create_task.call_args.args[1]
        self.assertIn("进程调度", request["sourceContent"])
        self.assertEqual(12, request["pageCount"])


if __name__ == "__main__":
    unittest.main()
