from app.multi_agents.diagram_common import DiagramMermaidAgent


class ActivityDiagramAgent(DiagramMermaidAgent):
    """Text-only activity-diagram specialist. Image generation is owned by image_agent."""

    def __init__(self) -> None:
        super().__init__("diagram_activity_agent", ("flowchart", "graph"))


diagram_activity_agent = ActivityDiagramAgent()

__all__ = ["ActivityDiagramAgent", "diagram_activity_agent"]
