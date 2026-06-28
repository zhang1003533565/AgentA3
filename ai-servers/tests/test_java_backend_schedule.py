import unittest

from app.services.java_backend import JavaBackendRetriever


class FakeScheduleRetriever(JavaBackendRetriever):
    def __init__(self):
        super().__init__()
        self.calls = []

    def _get_json(self, path, authorization, params=None):
        self.calls.append((path, params or {}))
        if path == "/api/schedule" and (params or {}).get("allSemesters") == "true":
            return {
                "code": 200,
                "msg": "success",
                "data": [
                    {
                        "id": 1,
                        "academicYear": "2025-2026",
                        "semesterTerm": 2,
                        "courseName": "数据结构",
                        "teacherName": "赵老师",
                        "location": "C301",
                        "weekday": 1,
                        "classSessions": "1-2节",
                        "weekRange": "1-16周",
                        "classCode": "CS201",
                    },
                    {
                        "id": 2,
                        "academicYear": "2024-2025",
                        "semesterTerm": 1,
                        "courseName": "Java程序设计",
                        "teacherName": "李老师",
                        "location": "B202",
                        "weekday": 2,
                        "classSessions": "3-4节",
                        "weekRange": "1-16周",
                        "classCode": "JAVA101",
                    },
                ],
            }
        if path == "/api/schedule":
            return {
                "code": 200,
                "msg": "success",
                "data": [
                    {
                        "id": 1,
                        "academicYear": "2025-2026",
                        "semesterTerm": 2,
                        "courseName": "数据结构",
                        "teacherName": "赵老师",
                        "location": "C301",
                        "weekday": 1,
                        "classSessions": "1-2节",
                        "weekRange": "1-16周",
                        "classCode": "CS201",
                    }
                ],
            }
        return {"code": 200, "msg": "success", "data": []}


class JavaBackendScheduleTest(unittest.TestCase):
    def test_course_lookup_falls_back_to_all_semesters_when_current_semester_misses(self):
        retriever = FakeScheduleRetriever()

        results = retriever.search_schedule("Bearer token", "java是什么时候上的呢？我上过java吗")

        names = {item.get("name") for item in results}
        self.assertIn("Java程序设计", names)
        java_course = next(item for item in results if item.get("name") == "Java程序设计")
        self.assertEqual("2024-2025 第 1 学期", java_course.get("semesterLabel"))
        self.assertEqual("all_semesters_fallback", java_course.get("queryScope"))
        self.assertEqual("current_semester_no_course_match", java_course.get("fallbackReason"))
        self.assertIn(("/api/schedule", {"allSemesters": "true"}), retriever.calls)


if __name__ == "__main__":
    unittest.main()
