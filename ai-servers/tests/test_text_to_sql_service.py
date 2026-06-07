import sqlite3
import tempfile
import unittest
from pathlib import Path

from app.rag.structured.text_to_sql import TextToSqlService


class TextToSqlServiceTest(unittest.TestCase):
    def test_execute_sqlite_readonly_query(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            db_path = Path(temp_dir) / "rag.db"
            with sqlite3.connect(db_path) as conn:
                conn.execute("CREATE TABLE dish (id INTEGER, name TEXT, category TEXT, taste TEXT, price INTEGER, rating REAL)")
                conn.execute("INSERT INTO dish VALUES (1, '黄焖鸡米饭', '米饭', '香辣', 18, 4.9)")

            service = TextToSqlService(sqlite_path=str(db_path))
            schema = service.introspect_sqlite_schema(str(db_path))
            result = service.plan("查询黄焖鸡", schema=schema)

            self.assertIn("SELECT", result.sql)
            self.assertEqual(1, len(result.rows))
            self.assertEqual("黄焖鸡米饭", result.rows[0]["name"])

    def test_rejects_non_readonly_sql(self):
        self.assertFalse(TextToSqlService().validate_readonly("DELETE FROM dish"))


if __name__ == "__main__":
    unittest.main()
