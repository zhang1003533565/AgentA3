from app.rag.core.types import RagTraceStep


class RagTracer:
    def step(self, stage: str, **detail) -> RagTraceStep:
        return RagTraceStep(stage=stage, detail=detail)
