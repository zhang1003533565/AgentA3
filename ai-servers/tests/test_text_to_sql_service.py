import os
import sqlite3
import tempfile
import unittest
from pathlib import Path

from app.rag.structured.text_to_sql import TextToSqlService


class TextToSqlServiceTest(unittest.TestCase):
    def test_execute_sqlite_readonly_query(self):
        old_path = os.environ.get("RAG_SQLITE_DB_PATH")
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                db_path = Path(temp_dir) / "rag.db"
                with sqlite3.connect(db_path) as conn:
                    conn.execute("CREATE TABLE dish (id INTEGER, name TEXT, category TEXT, taste TEXT, price INTEGER, rating REAL)")
                    conn.execute("INSERT INTO dish VALUES (1, '黄焖鸡米饭', '米饭', '香辣', 18, 4.9)")
                os.environ["RAG_SQLITE_DB_PATH"] = str(db_path)

                service = TextToSqlService()
                schema = service.introspect_sqlite_schema()
                result = service.plan("查询黄焖鸡", schema=schema)

                self.assertIn("SELECT", result.sql)
                self.assertEqual(1, len(result.rows))
                self.assertEqual("黄焖鸡米饭", result.rows[0]["name"])
        finally:
            if old_path is None:
                os.environ.pop("RAG_SQLITE_DB_PATH", None)
            else:
                os.environ["RAG_SQLITE_DB_PATH"] = old_path

    def test_rejects_non_readonly_sql(self):
        self.assertFalse(TextToSqlService().validate_readonly("DELETE FROM dish"))


if __name__ == "__main__":
    unittest.main()
