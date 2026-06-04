import unittest

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

    def test_mermaid_declarations_are_validated(self):
        mind_map = diagram_mind_map_agent.build_diagram(
            "进程调度",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nmindmap\n  root((进程调度))\n```"),
        )
        self.assertIn("mindmap", mind_map)

        flowchart = diagram_flowchart_agent.build_diagram(
            "括号匹配",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nflowchart TD\n  A[开始] --> B[结束]\n```"),
        )
        self.assertIn("flowchart TD", flowchart)

        activity = diagram_activity_agent.build_diagram(
            "任务流程",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nflowchart TD\n  A[开始] --> B[结束]\n```"),
        )
        self.assertIn("flowchart TD", activity)

        architecture = diagram_architecture_agent.build_diagram(
            "系统架构",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nflowchart LR\n  Web --> API\n```"),
        )
        self.assertIn("flowchart LR", architecture)

    def test_rejects_wrong_mermaid_type(self):
        with self.assertRaises(HTTPException):
            diagram_mind_map_agent.build_diagram(
                "进程调度",
                [],
                chat_service=FakeDiagramProvider("```mermaid\nflowchart TD\n  A --> B\n```"),
            )


if __name__ == "__main__":
    unittest.main()
