import json
import unittest

from fastapi import HTTPException

from app.multi_agents.activity_publish_agent.agent import (
    ACTIVITY_FIELDS,
    REQUIRED_FIELDS,
    activity_publish_agent,
    build_llm_payload,
    validate_and_serialize_answer,
)


CATEGORY_OPTIONS = [
    {"id": 1, "name": "文艺活动"},
    {"id": 2, "name": "体育活动"},
]


def empty_draft(**overrides):
    draft = {field: None for field in ACTIVITY_FIELDS}
    draft.update(overrides)
    return draft


def build_input(**overrides):
    base = {
        "userInput": "我要办一个校园歌手大赛",
        "activityDraft": empty_draft(),
        "generatedFields": [],
        "categoryOptions": CATEGORY_OPTIONS,
        "currentTime": "2026-08-22T15:30:00",
        "conversationContext": {},
    }
    base.update(overrides)
    return json.dumps(base, ensure_ascii=False)


def build_output(**overrides):
    base = {
        "action": "clarify",
        "reply": "好的，请告诉我活动时间和地点。",
        "activity": empty_draft(),
        "generatedFields": [],
        "missingFields": ["startTime", "endTime", "location", "maxPeople", "categoryId", "signupEndTime", "content"],
        "confidentFields": ["title"],
        "warnings": [],
    }
    base.update(overrides)
    return json.dumps(base, ensure_ascii=False)


class RecordingProvider:
    def __init__(self, answer):
        self.answer = answer
        self.user_prompt = ""

    def complete(self, system_prompt, user_prompt):
        self.user_prompt = user_prompt
        return self.answer


class ActivityPublishAgentTest(unittest.TestCase):
    def test_process_draft_when_content_generated(self):
        output = build_output(
            action="draft",
            reply="信息已齐全，content 为 AI 草稿，请确认。",
            activity=empty_draft(
                title="校园歌手大赛",
                categoryId=1,
                maxPeople=500,
                location="大学生活动中心",
                startTime="2026-09-10 14:00:00",
                endTime="2026-09-10 18:00:00",
                signupEndTime="2026-09-09 20:00:00",
                content="校园歌手大赛将于2026年9月10日14:00在大学生活动中心举行。",
            ),
            generatedFields=["content"],
            missingFields=[],
            confidentFields=["title", "categoryId", "maxPeople", "location", "startTime", "endTime", "signupEndTime"],
            warnings=["content 为 AI 生成的草稿，请确认"],
        )
        provider = RecordingProvider(output)
        result = activity_publish_agent.process(build_input(), [], chat_service=provider)
        payload = json.loads(result)
        self.assertEqual("draft", payload["action"])
        self.assertEqual(["content"], payload["generatedFields"])
        self.assertEqual([], payload["missingFields"])
        self.assertIn("content", json.loads(provider.user_prompt)["user_input"])

    def test_process_ready_after_confirmation(self):
        output = build_output(
            action="ready",
            reply="最终活动数据已生成，请确认提交。",
            activity=empty_draft(
                title="校园歌手大赛",
                categoryId=1,
                maxPeople=500,
                location="大学生活动中心",
                startTime="2026-09-10 14:00:00",
                endTime="2026-09-10 18:00:00",
                signupEndTime="2026-09-09 20:00:00",
                content="校园歌手大赛将于2026年9月10日14:00在大学生活动中心举行。",
            ),
            generatedFields=[],
            missingFields=[],
            confidentFields=[
                "title", "categoryId", "maxPeople", "location",
                "startTime", "endTime", "signupEndTime", "content",
            ],
        )
        result = activity_publish_agent.process(
            build_input(generatedFields=["content"]), [], chat_service=RecordingProvider(output)
        )
        self.assertEqual("ready", json.loads(result)["action"])

    def test_process_clarify_when_required_missing(self):
        output = build_output(
            action="clarify",
            reply="请问预计最多多少人报名？",
            activity=empty_draft(title="校园歌手大赛", location="大学生活动中心", startTime="2026-09-10 14:00:00"),
            missingFields=["endTime", "maxPeople", "categoryId", "signupEndTime", "content"],
            confidentFields=["title", "location", "startTime"],
        )
        result = activity_publish_agent.process(build_input(), [], chat_service=RecordingProvider(output))
        self.assertEqual("clarify", json.loads(result)["action"])
        self.assertIn("maxPeople", json.loads(result)["missingFields"])

    def test_signup_end_explicit_skip_allows_draft(self):
        output = build_output(
            action="draft",
            reply="报名截止时间已按不设置处理，content 为 AI 草稿，请确认。",
            activity=empty_draft(
                title="读书分享会",
                categoryId=1,
                maxPeople=100,
                location="图书馆",
                startTime="2026-08-29 15:00:00",
                endTime="2026-08-29 17:00:00",
                content="读书分享会将于2026年8月29日15:00在图书馆举行。",
            ),
            generatedFields=["content"],
            missingFields=[],
            confidentFields=["title", "categoryId", "maxPeople", "location", "startTime", "endTime"],
            warnings=["管理员明确不设置报名截止时间"],
        )
        result = activity_publish_agent.process(build_input(), [], chat_service=RecordingProvider(output))
        payload = json.loads(result)
        self.assertEqual("draft", payload["action"])
        self.assertIsNone(payload["activity"]["signupEndTime"])

    def test_rejects_markdown_fence(self):
        provider = RecordingProvider("```json\n" + build_output() + "\n```")
        with self.assertRaises(HTTPException):
            activity_publish_agent.process(build_input(), [], chat_service=provider)

    def test_rejects_extra_top_level_key(self):
        output = build_output(extraField="x")
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(output, CATEGORY_OPTIONS)

    def test_rejects_extra_activity_field(self):
        payload = json.loads(build_output())
        payload["activity"]["audience"] = "全校学生"
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)

    def test_rejects_max_people_zero(self):
        payload = json.loads(build_output(action="draft", missingFields=[], generatedFields=["content"]))
        payload["activity"] = empty_draft(
            title="活动", categoryId=1, maxPeople=0, location="礼堂",
            startTime="2026-09-10 14:00:00", endTime="2026-09-10 18:00:00",
            signupEndTime="2026-09-09 20:00:00", content="活动详情",
        )
        payload["confidentFields"] = ["title", "categoryId", "location", "startTime", "endTime", "signupEndTime", "content"]
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)

    def test_rejects_end_before_start(self):
        payload = json.loads(build_output(action="draft", missingFields=[], generatedFields=["content"]))
        payload["activity"] = empty_draft(
            title="活动", categoryId=1, maxPeople=10, location="礼堂",
            startTime="2026-09-10 18:00:00", endTime="2026-09-10 14:00:00",
            signupEndTime="2026-09-09 20:00:00", content="活动详情",
        )
        payload["confidentFields"] = ["title", "categoryId", "maxPeople", "location", "startTime", "endTime", "signupEndTime", "content"]
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)

    def test_rejects_category_id_not_in_options(self):
        payload = json.loads(build_output(action="draft", missingFields=[], generatedFields=["content"]))
        payload["activity"] = empty_draft(
            title="活动", categoryId=99, maxPeople=10, location="礼堂",
            startTime="2026-09-10 14:00:00", endTime="2026-09-10 18:00:00",
            signupEndTime="2026-09-09 20:00:00", content="活动详情",
        )
        payload["confidentFields"] = ["title", "categoryId", "maxPeople", "location", "startTime", "endTime", "signupEndTime", "content"]
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)

    def test_rejects_inconsistent_missing_fields(self):
        payload = json.loads(build_output())
        payload["activity"]["maxPeople"] = 100
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)

    def test_rejects_ready_with_generated_fields(self):
        output = build_output(action="ready", missingFields=[], generatedFields=["content"])
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(output, CATEGORY_OPTIONS)

    def test_plain_text_input_is_wrapped_into_contract(self):
        payload = build_llm_payload("我要办一个校园歌手大赛")
        self.assertEqual("我要办一个校园歌手大赛", payload["userInput"])
        self.assertEqual(ACTIVITY_FIELDS, tuple(payload["activityDraft"].keys()))
        self.assertTrue(all(value is None for value in payload["activityDraft"].values()))
        self.assertEqual([], payload["generatedFields"])
        self.assertEqual([], payload["categoryOptions"])
        self.assertIsNone(payload["currentTime"])
        self.assertEqual({}, payload["conversationContext"])

    def test_plain_text_input_process_end_to_end(self):
        output = build_output(
            action="clarify",
            reply="好的，请告诉我活动时间和地点。",
            activity=empty_draft(title="校园歌手大赛"),
            missingFields=["startTime", "endTime", "location", "maxPeople", "categoryId", "signupEndTime", "content"],
            confidentFields=["title"],
        )
        provider = RecordingProvider(output)
        result = activity_publish_agent.process("我要办一个校园歌手大赛", [], chat_service=provider)
        self.assertEqual("clarify", json.loads(result)["action"])
        wrapped = json.loads(json.loads(provider.user_prompt)["user_input"])
        self.assertEqual("我要办一个校园歌手大赛", wrapped["userInput"])
        self.assertEqual(10, len(wrapped["activityDraft"]))

    def test_contract_json_input_keeps_original_behavior(self):
        contract_input = build_input(
            userInput="更新活动地点",
            activityDraft=empty_draft(title="校园歌手大赛", location="旧礼堂", maxPeople=100),
            categoryOptions=[{"id": 2, "name": "体育活动"}],
        )
        payload = build_llm_payload(contract_input)
        self.assertEqual("更新活动地点", payload["userInput"])
        self.assertEqual("校园歌手大赛", payload["activityDraft"]["title"])
        self.assertEqual("旧礼堂", payload["activityDraft"]["location"])
        self.assertEqual(100, payload["activityDraft"]["maxPeople"])
        self.assertEqual([{"id": 2, "name": "体育活动"}], payload["categoryOptions"])

    def test_rejects_missing_required_without_missing_fields(self):
        output = build_output(
            action="ready",
            reply="数据齐全。",
            activity=empty_draft(title="活动", categoryId=1, maxPeople=10, location="礼堂",
                                 startTime="2026-09-10 14:00:00", endTime="2026-09-10 18:00:00",
                                 signupEndTime="2026-09-09 20:00:00", content="活动详情"),
            missingFields=[],
            generatedFields=[],
            confidentFields=list(REQUIRED_FIELDS),
        )
        payload = json.loads(output)
        payload["activity"]["maxPeople"] = None
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)

    def test_rejects_bad_time_format(self):
        payload = json.loads(build_output(action="draft", missingFields=[], generatedFields=["content"]))
        payload["activity"] = empty_draft(
            title="活动", categoryId=1, maxPeople=10, location="礼堂",
            startTime="9月10日下午2点", endTime="2026-09-10 18:00:00",
            signupEndTime="2026-09-09 20:00:00", content="活动详情",
        )
        payload["confidentFields"] = ["title", "categoryId", "maxPeople", "location", "startTime", "endTime", "signupEndTime", "content"]
        with self.assertRaises(HTTPException):
            validate_and_serialize_answer(json.dumps(payload, ensure_ascii=False), CATEGORY_OPTIONS)


if __name__ == "__main__":
    unittest.main()
