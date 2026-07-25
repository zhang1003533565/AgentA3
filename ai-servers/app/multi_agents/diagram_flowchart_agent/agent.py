from app.multi_agents.diagram_common import DiagramMermaidAgent


class FlowchartDiagramAgent(DiagramMermaidAgent):
    """Text-only flowchart specialist. Image generation is owned by image_agent."""

    def __init__(self) -> None:
        super().__init__("diagram_flowchart_agent", ("flowchart", "graph"))


diagram_flowchart_agent = FlowchartDiagramAgent()

__all__ = ["FlowchartDiagramAgent", "diagram_flowchart_agent"]
