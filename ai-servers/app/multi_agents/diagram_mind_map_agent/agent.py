from app.multi_agents.diagram_common import DiagramMermaidAgent


class DiagramMindMapAgent(DiagramMermaidAgent):
    def __init__(self) -> None:
        super().__init__("diagram_mind_map_agent", ("mindmap",))

    def build_mind_map(self, topic, evidence, chat_service=None):
        return self.build_diagram(topic, evidence, chat_service=chat_service)


diagram_mind_map_agent = DiagramMindMapAgent()

__all__ = ["DiagramMindMapAgent", "diagram_mind_map_agent"]
