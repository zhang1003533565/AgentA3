from dataclasses import dataclass
from typing import Any, Dict, List, Optional
import re
import sqlite3
from pathlib import Path


@dataclass
class SqlQueryResult:
    sql: str
    rows: List[Dict[str, Any]]
    error: str = ""


class TextToSqlService:
    def generate_sql(self, user_query: str, schema: Dict[str, Any]) -> str:
        query = (user_query or "").strip()
        compact = re.sub(r"\s+", "", query.lower())
        schema = schema or self.default_schema()
        table = self._route_table(compact, schema)
        keyword = self._extract_keyword(query)
        columns = ", ".join(self._safe_identifier(column) for column in schema.get(table, {}).get("columns", ["*"]))
        where = ""
        if keyword:
            searchable = schema.get(table, {}).get("searchable", [])
            escaped = keyword.replace("'", "''")
            predicates = [f"{self._safe_identifier(column)} LIKE '%{escaped}%'" for column in searchable]
            if predicates:
                where = " WHERE " + " OR ".join(predicates)
        return f"SELECT {columns} FROM {self._safe_identifier(table)}{where} LIMIT 20"

    def validate_readonly(self, sql: str) -> bool:
        blocked = ("insert", "update", "delete", "drop", "alter", "truncate")
        normalized = re.sub(r"\s+", " ", (sql or "").strip().lower())
        return normalized.startswith("select") and not any(token in normalized for token in blocked)

    def plan(self, user_query: str, schema: Optional[Dict[str, Any]] = None) -> SqlQueryResult:
        active_schema = schema or self.default_schema()
        try:
            sql = self.generate_sql(user_query, active_schema)
        except Exception as exc:
            return SqlQueryResult(sql="", rows=[], error=str(exc))
        if not self.validate_readonly(sql):
            return SqlQueryResult(sql="", rows=[], error="unsafe_sql")
        try:
            return SqlQueryResult(sql=sql, rows=self.execute_sql(sql))
        except Exception as exc:
            return SqlQueryResult(sql=sql, rows=[], error=str(exc))

    def execute_sql(self, sql: str) -> List[Dict[str, Any]]:
        if not self.validate_readonly(sql):
            raise ValueError("Only readonly SELECT SQL is allowed")
        return []

    def introspect_sqlite_schema(self, sqlite_path: Optional[str] = None) -> Dict[str, Any]:
        active_path = sqlite_path or ""
        if not active_path:
            return self.default_schema()
        path = Path(active_path)
        if not path.exists():
            return self.default_schema()
        schema: Dict[str, Any] = {}
        uri = f"file:{path.resolve()}?mode=ro"
        with sqlite3.connect(uri, uri=True) as conn:
            table_rows = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            ).fetchall()
            for (table_name,) in table_rows:
                columns = [row[1] for row in conn.execute(f"PRAGMA table_info({table_name})").fetchall()]
                schema[table_name] = {"columns": columns, "searchable": columns}
        return schema or self.default_schema()

    def default_schema(self) -> Dict[str, Any]:
        return {
            "course_schedule": {
                "columns": ["id", "course_name", "teacher_name", "location", "weekday", "class_sessions"],
                "searchable": ["course_name", "teacher_name", "location"],
            },
            "dish": {
                "columns": ["id", "name", "stall_id", "category", "taste", "price", "rating"],
                "searchable": ["name", "category", "taste"],
            },
            "canteen_stall": {
                "columns": ["id", "stall_name", "restaurant_id", "category", "location", "avg_price"],
                "searchable": ["stall_name", "category", "location"],
            },
            "promotion_coupon": {
                "columns": ["id", "coupon_name", "merchant_name", "pickup_location", "start_date", "end_date"],
                "searchable": ["coupon_name", "merchant_name", "pickup_location"],
            },
        }

    def _route_table(self, compact_query: str, schema: Dict[str, Any]) -> str:
        if any(token in compact_query for token in ("课表", "课程", "上课")):
            return "course_schedule"
        if any(token in compact_query for token in ("优惠", "券", "活动", "满减")):
            return "promotion_coupon"
        if any(token in compact_query for token in ("档口", "食堂", "餐厅", "窗口")):
            return "canteen_stall"
        if any(token in compact_query for token in ("菜", "饭", "面", "饮品", "黄焖鸡", "麻辣烫")):
            return "dish"
        return next(iter(schema.keys()))

    def _extract_keyword(self, query: str) -> str:
        cleaned = re.sub(r"(查询|统计|列表|有哪些|有多少|多少个|排名|请问|帮我|一下|的)", "", query)
        cleaned = re.sub(r"[^\w\u4e00-\u9fff]+", "", cleaned)
        return cleaned[:24]

    def _safe_identifier(self, value: str) -> str:
        if value == "*":
            return value
        if not re.fullmatch(r"[a-zA-Z_][a-zA-Z0-9_]*", value or ""):
            raise ValueError(f"Unsafe SQL identifier: {value}")
        return value
