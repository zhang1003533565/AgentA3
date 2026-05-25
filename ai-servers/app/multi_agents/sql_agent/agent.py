from typing import Any, Dict

from app.rag.structured.text_to_sql import TextToSqlService


class SqlAgent:
    def __init__(self) -> None:
        self.text_to_sql = TextToSqlService()

    def generate_sql(self, user_query: str, schema: Dict[str, Any]) -> str:
        return self.text_to_sql.generate_sql(user_query, schema)


sql_agent = SqlAgent()
