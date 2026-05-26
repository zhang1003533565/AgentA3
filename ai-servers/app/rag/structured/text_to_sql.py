from dataclasses import dataclass
from typing import Any, Dict, List, Optional
import re


@dataclass
class SqlQueryResult:
    sql: str
    rows: List[Dict[str, Any]]


class TextToSqlService:
    def generate_sql(self, user_query: str, schema: Dict[str, Any]) -> str:
        query = (user_query or "").strip()
        compact = re.sub(r"\s+", "", query.lower())
        schema = schema or self.default_schema()
        table = self._route_table(compact, schema)
        keyword = self._extract_keyword(query)
        columns = ", ".join(schema.get(table, {}).get("columns", ["*"]))
        where = ""
        if keyword:
            searchable = schema.get(table, {}).get("searchable", [])
            escaped = keyword.replace("'", "''")
            predicates = [f"{column} LIKE '%{escaped}%'" for column in searchable]
            if predicates:
                where = " WHERE " + " OR ".join(predicates)
        return f"SELECT {columns} FROM {table}{where} LIMIT 20"

    def validate_readonly(self, sql: str) -> bool:
        blocked = ("insert", "update", "delete", "drop", "alter", "truncate")
        normalized = re.sub(r"\s+", " ", (sql or "").strip().lower())
        return normalized.startswith("select") and not any(token in normalized for token in blocked)

    def plan(self, user_query: str, schema: Optional[Dict[str, Any]] = None) -> SqlQueryResult:
        active_schema = schema or self.default_schema()
        sql = self.generate_sql(user_query, active_schema)
        if not self.validate_readonly(sql):
            return SqlQueryResult(sql="", rows=[])
        return SqlQueryResult(sql=sql, rows=[])

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
