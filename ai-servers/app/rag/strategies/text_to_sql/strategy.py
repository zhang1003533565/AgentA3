from app.rag.core import BaseRagStrategy, RAG_STRATEGY_SPECS, RagDocument, RagQuery, RagResult, RagTraceStep
from app.rag.structured.text_to_sql import TextToSqlService

spec = RAG_STRATEGY_SPECS["text_to_sql"]


class TextToSqlStrategy(BaseRagStrategy):
    name = "text_to_sql"
    category = spec["category"]

    def __init__(self) -> None:
        self.service = TextToSqlService()

    def run(self, query: RagQuery) -> RagResult:
        plan = self.service.plan(query.text)
        document = RagDocument(
            id="text_to_sql:plan",
            content=f"SQL 查询计划：{plan.sql or '未生成安全 SQL'}",
            source="text_to_sql",
            score=1.0 if plan.sql else 0.0,
            metadata={"sql": plan.sql, "readonly": bool(plan.sql), "rows": plan.rows},
        )
        return RagResult(
            strategy=self.name,
            documents=[document] if plan.sql else [],
            trace=[
                RagTraceStep(stage="generate_sql", detail={"readonly": bool(plan.sql), "sql": plan.sql}),
            ],
            metadata={"implemented": True, "sql": plan.sql, "readonly": bool(plan.sql)},
        )


strategy = TextToSqlStrategy()
