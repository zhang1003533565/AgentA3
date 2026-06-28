import unittest

from app.services.memory_store import MemoryStore


class MemoryStoreTest(unittest.TestCase):
    def test_context_turns_are_compacted_and_latest_subject_is_kept(self):
        store = MemoryStore()
        store._redis_client = None
        store.context_recent_turns = 3
        token = "session-context-token"

        for index in range(7):
            store.append_context_turn(
                token,
                f"课程{index}什么时候上",
                f"课程名：课程{index}\n周一 1-2节",
                metadata={"intent": "course_time", "toolName": "java_schedule_api"},
            )

        context = store.get_context(token)

        self.assertEqual(3, len(context["turns"]))
        self.assertEqual(4, context["compressedTurnCount"])
        self.assertIn("用户问：课程0什么时候上", context["summary"])
        self.assertEqual("课程6", context["lastSubjects"][0])
        self.assertEqual("java_schedule_api", context["turns"][-1]["metadata"]["toolName"])


if __name__ == "__main__":
    unittest.main()
