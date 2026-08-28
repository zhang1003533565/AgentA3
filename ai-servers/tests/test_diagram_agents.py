import importlib
import unittest

from app.multi_agents.catalog import get_agent_catalog, normalize_agent_name
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
    def test_catalog_contains_text_only_diagram_agents(self):
        catalog = {item["name"]: item for item in get_agent_catalog()["agents"]}
        for name in (
            "diagram_mind_map_agent",
            "diagram_flowchart_agent",
            "diagram_architecture_agent",
        ):
            self.assertIn(name, catalog)
            self.assertEqual(["text"], catalog[name]["requiredModelModalities"])
        self.assertEqual(["image"], catalog["image_agent"]["requiredModelModalities"])
        self.assertNotIn("diagram_activity_agent", catalog)
        self.assertNotIn("diagram_activity_prompt_agent", catalog)
        self.assertNotIn("knowledge_graph_prompt_agent", catalog)
        self.assertNotIn("ppt_image_agent", catalog)

    def test_aliases_route_to_prefixed_agents(self):
        self.assertEqual("diagram_mind_map_agent", normalize_agent_name("思维导图"))
        self.assertEqual("diagram_flowchart_agent", normalize_agent_name("流程图"))
        self.assertEqual("diagram_architecture_agent", normalize_agent_name("架构图"))
        self.assertIsNone(normalize_agent_name("活动图"))
        self.assertIsNone(normalize_agent_name("mind_map_agent"))

    def test_rule_router_uses_visual_tools_instead_of_agent_delegation(self):
        cases = {
            "生成进程调度思维导图": "generate_mind_map_tool",
            "生成括号匹配流程图": "generate_flowchart_tool",
            "生成会议任务活动图": "generate_image_tool",
            "生成系统架构图": "generate_architecture_tool",
            "生成操作系统知识图谱": "generate_image_tool",
            "生成一张教学配图": "generate_image_tool",
            "生成 PPT 封面配图": "generate_image_tool",
        }
        for query, tool_name in cases.items():
            plan = leader_agent._plan_with_rules(query)
            self.assertEqual("call_tool", plan.action)
            self.assertEqual("leader_agent", plan.target_agent)
            self.assertEqual(tool_name, plan.tool_name)

    def test_only_image_agent_imports_the_image_provider(self):
        image_module = importlib.import_module("app.multi_agents.image_agent.agent")
        self.assertTrue(hasattr(image_module, "get_qwen_image_provider"))
        for module_name in (
            "app.api.routes.images",
            "app.multi_agents.diagram_mind_map_agent.agent",
            "app.multi_agents.diagram_flowchart_agent.agent",
            "app.multi_agents.diagram_architecture_agent.agent",
        ):
            module = importlib.import_module(module_name)
            self.assertFalse(hasattr(module, "get_qwen_image_provider"), module_name)

    def test_structured_diagram_tools_are_registered_without_prompt_agents(self):
        rag_routes = importlib.import_module("app.api.routes.rag")
        self.assertEqual(
            {"generate_image_tool"},
            set(rag_routes.VISUAL_GENERATION_TOOL_CONFIG),
        )
        self.assertEqual(
            {
                "generate_mind_map_tool",
                "generate_flowchart_tool",
                "generate_architecture_tool",
            },
            set(rag_routes.STRUCTURED_DIAGRAM_TOOL_CONFIG),
        )
        callable_catalog = rag_routes._build_leader_callable_catalog()
        catalog_agent_names = {item["name"] for item in get_agent_catalog()["agents"]}
        callable_tools = {item["name"] for item in callable_catalog["tools"]}
        self.assertGreaterEqual(
            callable_tools,
            {
                "generate_image_tool",
                "generate_mind_map_tool",
                "generate_flowchart_tool",
                "generate_architecture_tool",
            },
        )
        self.assertNotIn("mind_map_agent", catalog_agent_names)
        self.assertNotIn("diagram_flowchart_prompt_agent", catalog_agent_names)
        self.assertNotIn("architecture_prompt_agent", catalog_agent_names)
        self.assertIn("image_agent", catalog_agent_names)

    def test_diagram_agents_only_return_mermaid_text(self):
        mind_map = diagram_mind_map_agent.build_mind_map(
            "进程调度",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nmindmap\n  root((进程调度))\n```")
        )
        flowchart = diagram_flowchart_agent.build_diagram(
            "括号匹配",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nflowchart TD\n  A --> B\n```")
        )
        architecture = diagram_architecture_agent.build_diagram(
            "系统架构",
            [],
            chat_service=FakeDiagramProvider("```mermaid\nflowchart LR\n  Web --> API\n```")
        )
        self.assertTrue(mind_map.startswith("```mermaid\nmindmap"))
        self.assertTrue(flowchart.startswith("```mermaid\nflowchart"))
        self.assertTrue(architecture.startswith("```mermaid\nflowchart"))


if __name__ == "__main__":
    unittest.main()
