import unittest
from unittest.mock import patch

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
    def test_java_backend_url_can_be_injected_for_container_runtime(self):
        with patch.dict("os.environ", {"JAVA_BACKEND_BASE_URL": "http://backend:8080/"}):
            retriever = JavaBackendRetriever()

        self.assertEqual("http://backend:8080", retriever.java_base_url)

    def test_blank_java_backend_url_keeps_local_development_default(self):
        with patch.dict("os.environ", {"JAVA_BACKEND_BASE_URL": ""}):
            retriever = JavaBackendRetriever()

        self.assertEqual("http://localhost:8080", retriever.java_base_url)

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


class FakeFacilityRetriever(JavaBackendRetriever):
    def __init__(self):
        super().__init__()
        self.calls = []

    def _get_json(self, path, authorization, params=None):
        self.calls.append((path, params or {}))
        if path == "/api/v1/map/locate":
            return {"code": 404, "message": "未找到匹配的目的地", "data": None}
        if path == "/api/v1/facility/list":
            return {
                "code": 200,
                "data": {
                    "records": [
                        {
                            "id": 9001,
                            "facilityName": "学一食堂",
                            "facilityType": 1,
                            "location": "朝阳校区东区",
                            "status": 1,
                        }
                    ]
                },
            }
        return {"code": 200, "data": []}


class JavaBackendFacilityTest(unittest.TestCase):
    def test_generic_facility_query_falls_back_to_facility_list(self):
        retriever = FakeFacilityRetriever()
        results = retriever._search_facilities("Bearer test", "请查询校园设施及其位置信息。")
        self.assertEqual(1, len(results))
        self.assertEqual("学一食堂", results[0]["name"])
        self.assertTrue(any(path == "/api/v1/facility/list" for path, _ in retriever.calls))
        self.assertFalse(any(path == "/api/v1/map/locate" for path, _ in retriever.calls))

    def test_navigation_query_still_tries_map_locate(self):
        retriever = FakeFacilityRetriever()
        retriever._search_facilities("Bearer test", "图书馆在哪里")
        self.assertTrue(any(path == "/api/v1/map/locate" for path, _ in retriever.calls))
        self.assertTrue(any(path == "/api/v1/facility/list" for path, _ in retriever.calls))


if __name__ == "__main__":
    unittest.main()
