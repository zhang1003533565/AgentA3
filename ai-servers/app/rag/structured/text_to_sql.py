from dataclasses import dataclass
from typing import Any, Dict, List


@dataclass
class SqlQueryResult:
    sql: str
    rows: List[Dict[str, Any]]


class TextToSqlService:
    def generate_sql(self, user_query: str, schema: Dict[str, Any]) -> str:
        return ""

    def validate_readonly(self, sql: str) -> bool:
        blocked = ("insert", "update", "delete", "drop", "alter", "truncate")
        normalized = sql.strip().lower()
        return normalized.startswith("select") and not any(token in normalized for token in blocked)
