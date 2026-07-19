from app.multi_agents.diagram_common import DiagramMermaidAgent


class DiagramArchitectureAgent(DiagramMermaidAgent):
    """Text-only architecture specialist. Image generation is owned by image_agent."""

    def __init__(self) -> None:
        super().__init__("diagram_architecture_agent", ("flowchart", "graph", "architecture-beta"))


diagram_architecture_agent = DiagramArchitectureAgent()

__all__ = ["DiagramArchitectureAgent", "diagram_architecture_agent"]
