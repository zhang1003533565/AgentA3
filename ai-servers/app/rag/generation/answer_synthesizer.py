from typing import Any, Dict, List, Optional

from app.rag.core.types import RagDocument


class AnswerSynthesizer:
    """Lightweight local answer composer used when a strategy only returns evidence."""

    def synthesize(
        self,
        query: str,
        documents: List[RagDocument],
        strategy: str,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> str:
        normalized_query = (query or "").strip()
        if not documents:
            return f"未检索到和“{normalized_query}”直接相关的资料，建议补充知识库或换一个 RAG 策略重试。"

        sql_answer = self._synthesize_sql(documents)
        if sql_answer:
            return sql_answer

        bullets = []
        for index, document in enumerate(documents[:3], start=1):
            snippet = self._snippet(document.content)
            source = document.source or document.id
            bullets.append(f"{index}. {snippet}（来源：{source}）")

        prefix = f"基于 `{strategy}` 检索到 {len(documents)} 条资料，和“{normalized_query}”相关的信息如下："
        return prefix + "\n" + "\n".join(bullets)

    def _synthesize_sql(self, documents: List[RagDocument]) -> str:
        sql_documents = [document for document in documents if document.source == "text_to_sql"]
        if not sql_documents:
            return ""

        document = sql_documents[0]
        sql = str(document.metadata.get("sql") or "")
        rows = document.metadata.get("rows")
        if not isinstance(rows, list):
            rows = []

        if not rows:
            return f"已生成只读 SQL：{sql}\n当前没有查询到结果。"

        previews = []
        for row in rows[:5]:
            if isinstance(row, dict):
                previews.append("，".join(f"{key}={value}" for key, value in row.items()))
            else:
                previews.append(str(row))
        return f"已生成只读 SQL：{sql}\n查询到 {len(rows)} 条结果：\n" + "\n".join(f"{idx}. {text}" for idx, text in enumerate(previews, start=1))

    def _snippet(self, content: str) -> str:
        normalized = " ".join((content or "").split())
        if len(normalized) <= 180:
            return normalized
        return normalized[:177] + "..."
