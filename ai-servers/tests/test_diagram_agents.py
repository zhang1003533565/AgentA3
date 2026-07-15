import importlib
import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi import HTTPException

from app.multi_agents.catalog import get_agent_catalog, normalize_agent_name
from app.multi_agents.diagram_activity_agent.agent import diagram_activity_agent
from app.multi_agents.diagram_architecture_agent.agent import diagram_architecture_agent
from app.multi_agents.diagram_flowchart_agent.agent import diagram_flowchart_agent
from app.multi_agents.diagram_mind_map_agent.agent import diagram_mind_map_agent
from app.multi_agents.leader_agent.agent import leader_agent


class FakeDiagramProvider:
    def __init__(self, answer):
        self.answer = answer

    def complete(self, system_prompt, user_prompt):
        return self.answer


class FakeImageProvider:
    def __init__(self):
        self.requests = []

    def generate(self, request):
        self.requests.append(request)
        return SimpleNamespace(model_dump=lambda: {
            "status": "success",
            "prompt": request.prompt,
            "metadata": request.metadata,
        })


class DiagramAgentsTest(unittest.TestCase):
    def test_catalog_contains_prefixed_diagram_agents(self):
        names = {item["name"] for item in get_agent_catalog()["agents"]}
        self.assertIn("diagram_mind_map_agent", names)
        self.assertIn("diagram_flowchart_agent", names)
        self.assertIn("diagram_activity_agent", names)
        self.assertIn("diagram_architecture_agent", names)

    def test_aliases_route_to_prefixed_agents(self):
        self.assertEqual("diagram_mind_map_agent", normalize_agent_name("思维导图"))
        self.assertEqual("diagram_flowchart_agent", normalize_agent_name("流程图"))
        self.assertEqual("diagram_activity_agent", normalize_agent_name("活动图"))
        self.assertEqual("diagram_architecture_agent", normalize_agent_name("架构图"))
        self.assertEqual("mind_map_agent", normalize_agent_name("mind_map_agent"))

    def test_rule_router_prefers_diagram_agents(self):
        self.assertEqual("diagram_mind_map_agent", leader_agent._plan_with_rules("生成进程调度思维导图").target_agent)
        self.assertEqual("diagram_flowchart_agent", leader_agent._plan_with_rules("生成括号匹配流程图").target_agent)
        self.assertEqual("diagram_activity_agent", leader_agent._plan_with_rules("生成会议任务活动图").target_agent)
        self.assertEqual("diagram_architecture_agent", leader_agent._plan_with_rules("生成系统架构图").target_agent)
        self.assertEqual("ppt_to_docx_agent", leader_agent._plan_with_rules("把 PPTX 转 DOCX，图片保留").target_agent)

    def test_diagram_agents_use_current_image_generation_contract(self):
        mind_map_provider = FakeImageProvider()
        with patch.object(
            importlib.import_module("app.multi_agents.diagram_mind_map_agent.agent"),
            "get_qwen_image_provider",
            return_value=mind_map_provider,
        ):
            mind_map = diagram_mind_map_agent.generate_mind_map_image("进程调度")
        self.assertEqual("success", mind_map["status"])
        self.assertEqual("mindmap", mind_map_provider.requests[0].metadata["diagramType"])

        flowchart_provider = FakeImageProvider()
        with patch.object(
            importlib.import_module("app.multi_agents.diagram_flowchart_agent.agent"),
            "get_qwen_image_provider",
            return_value=flowchart_provider,
        ):
            flowchart = json.loads(diagram_flowchart_agent.generate_images_json("括号匹配", []))
        self.assertEqual("success", flowchart["status"])
        self.assertEqual("flowchart", flowchart_provider.requests[0].metadata["diagramType"])

        activity_provider = FakeImageProvider()
        with patch.object(
            importlib.import_module("app.multi_agents.diagram_activity_agent.agent"),
            "get_qwen_image_provider",
            return_value=activity_provider,
        ):
            activity = json.loads(diagram_activity_agent.generate_images_json("任务流程", []))
        self.assertEqual("success", activity["status"])
        self.assertEqual("activity", activity_provider.requests[0].metadata["diagramType"])

        architecture_provider = FakeImageProvider()
        with patch.object(
            importlib.import_module("app.multi_agents.diagram_architecture_agent.agent"),
            "get_qwen_image_provider",
            return_value=architecture_provider,
        ):
            architecture = diagram_architecture_agent.generate_images("系统架构", [])
        self.assertEqual("success", architecture["status"])
        self.assertEqual("architecture", architecture_provider.requests[0].chartType)

    def test_rejects_empty_diagram_prompt(self):
        with self.assertRaises(HTTPException):
            diagram_flowchart_agent.generate_images_json("", [])
        with self.assertRaises(ValueError):
            diagram_mind_map_agent.generate_mind_map_image("")


if __name__ == "__main__":
    unittest.main()
