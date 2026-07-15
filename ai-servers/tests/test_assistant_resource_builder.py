import copy
import hashlib
import json

import pytest

from app.services import assistant_resource_builder as resource_builder
from app.services.assistant_resource_builder import (
    BUSINESS_CARD_FIELDS,
    MAX_ENVELOPE_BYTES,
    build_assistant_resource_bundle,
    verify_evidence_integrity,
)


BUSINESS_CASES = {
    "course": (
        {
            "businessId": "course-42",
            "courseName": "数据结构",
            "teacherName": "李老师",
            "weekday": 2,
            "startSection": 1,
            "endSection": 2,
            "classroom": "A101",
            "weekText": "1-16周",
        },
        {
            "businessId",
            "courseName",
            "teacherName",
            "weekday",
            "startSection",
            "endSection",
            "classroom",
            "weekText",
        },
    ),
    "activity": (
        {
            "businessId": "activity-7",
            "title": "算法讲座",
            "category": "讲座",
            "startTime": "2026-07-15T10:00:00Z",
            "endTime": "2026-07-15T11:00:00Z",
            "location": "报告厅",
            "status": "open",
        },
        {"businessId", "title", "category", "startTime", "endTime", "location", "status"},
    ),
    "meeting": (
        {
            "businessId": "meeting-9",
            "title": "项目周会",
            "startTime": "2026-07-15T10:00:00Z",
            "endTime": "2026-07-15T11:00:00Z",
            "location": "B203",
            "status": "scheduled",
        },
        {"businessId", "title", "startTime", "endTime", "location", "status"},
    ),
    "dining": (
        {
            "businessId": "dining-3",
            "name": "一食堂",
            "category": "食堂",
            "location": "东区",
            "openingHours": "07:00-21:00",
            "rating": 4.8,
            "priceRange": "10-20",
            "imageUrl": "https://cdn.example.edu/dining.png",
        },
        {"businessId", "name", "category", "location", "openingHours", "rating", "priceRange", "imageUrl"},
    ),
    "facility": (
        {
            "businessId": "facility-2",
            "name": "图书馆",
            "category": "学习空间",
            "location": "中心区",
            "openingHours": "08:00-22:00",
            "status": "open",
            "longitude": 120.1,
            "latitude": 30.2,
        },
        {"businessId", "name", "category", "location", "openingHours", "status", "longitude", "latitude"},
    ),
    "secondhand": (
        {
            "businessId": "secondhand-5",
            "title": "二手教材",
            "category": "图书",
            "price": 20,
            "condition": "九成新",
            "status": "available",
            "createdAt": "2026-07-14T09:00:00Z",
            "imageUrl": "https://cdn.example.edu/book.png",
        },
        {"businessId", "title", "category", "price", "condition", "status", "createdAt", "imageUrl"},
    ),
}


def build_bundle(**overrides):
    arguments = {
        "answer": "这里是回答",
        "answer_type": "text",
        "documents": [],
        "trace": [],
        "metadata": {},
        "attachments": [],
        "request_context": {"requestId": "req-test", "agent": "leader_agent", "model": "test-model"},
    }
    arguments.update(overrides)
    return build_assistant_resource_bundle(**arguments)


def test_attachment_becomes_typed_file_resource_without_false_grounding():
    bundle = build_bundle(
        attachments=[
            {
                "fileName": "复习资料.docx",
                "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "storageKey": "export-1.docx",
                "size": 1234,
                "sha256": "a" * 64,
                "serverGenerated": True,
                "capability": "must-never-leak",
            }
        ]
    )

    resource = bundle["resources"][0]
    assert resource["deliveryType"] == "document"
    assert resource["groundingStatus"] == "model_only"
    assert resource["payload"] == {
        "type": "file",
        "format": "docx",
        "size": 1234,
        "digest": f"sha256:{'a' * 64}",
    }
    assert resource["evidenceIds"] == []
    assert bundle["evidenceChain"]["status"] == "model_only"
    assert "capability" not in json.dumps(bundle, ensure_ascii=False).lower()


def test_internal_only_attachment_is_ignored_in_favor_of_answer_content():
    bundle = build_bundle(attachments=[{"capability": "internal-only"}])

    assert len(bundle["resources"]) == 1
    assert bundle["resources"][0]["deliveryType"] == "content"
    assert bundle["resources"][0]["payload"]["content"] == "这里是回答"


def test_answer_without_attachment_becomes_typed_content_resource():
    bundle = build_bundle(answer="```python\nprint('ok')\n```", answer_type="code")

    assert len(bundle["resources"]) == 1
    resource = bundle["resources"][0]
    assert resource["kind"] == "code_example"
    assert resource["deliveryType"] == "content"
    assert resource["payload"] == {
        "type": "content",
        "content": "```python\nprint('ok')\n```",
        "language": "python",
    }


@pytest.mark.parametrize("kind", BUSINESS_CASES)
def test_business_cards_use_exact_allowlist_and_strip_pii(kind):
    allowed_values, allowed_fields = BUSINESS_CASES[kind]
    item = {
        **allowed_values,
        "userId": 10,
        "seller_id": 11,
        "PHONE": "13800000000",
        "contact": "secret@example.edu",
        "memberList": ["甲", "乙"],
        "participants": ["甲"],
        "transcript": "private",
        "access_token": "secret",
        "raw": {"internal": True},
        "unknown": "drop-me",
    }
    bundle = build_bundle(
        documents=[
            {
                "id": f"{kind}:1",
                "content": f"{kind} factual result",
                "source": "java_backend",
                "metadata": {"kind": kind, "item": item, "internalSecret": "drop-me"},
            }
        ]
    )

    card = next(resource for resource in bundle["resources"] if resource["kind"] == kind)
    assert set(card["payload"]) == {"type", *allowed_fields}
    assert card["payload"]["type"] == "business"
    assert {key: card["payload"][key] for key in allowed_fields} == allowed_values
    assert set(BUSINESS_CARD_FIELDS[kind]) == allowed_fields
    serialized = json.dumps(card, ensure_ascii=False).lower().replace("_", "")
    for forbidden in ("userid", "sellerid", "phone", "contact", "memberlist", "participants", "transcript", "token", "raw"):
        assert forbidden not in serialized


@pytest.mark.parametrize(
    ("kind", "expected_action"),
    [
        ("course", "open_resource"),
        ("activity", "open_resource"),
        ("meeting", "open_resource"),
        ("dining", "follow_up"),
        ("facility", "follow_up"),
        ("secondhand", "open_resource"),
    ],
)
def test_business_cards_only_offer_open_when_the_app_has_a_detail_route(kind, expected_action):
    allowed_values, _ = BUSINESS_CASES[kind]
    bundle = build_bundle(
        documents=[
            {
                "id": f"{kind}:route-contract",
                "content": f"{kind} factual result",
                "source": "java_backend",
                "metadata": {"kind": kind, "item": allowed_values},
            }
        ]
    )

    card = next(resource for resource in bundle["resources"] if resource["kind"] == kind)
    assert [action["type"] for action in card["actions"]] == [expected_action]


def test_source_digests_and_ids_are_deterministic_and_metadata_is_safe():
    document = {
        "id": "doc-9",
        "content": "确定性的知识来源",
        "source": "knowledge_base",
        "metadata": {
            "title": "教材第九章",
            "sourceVersion": "2026-07-14T08:00:00Z",
            "location": "chapter-9",
            "route": "/knowledge/9",
            "authorization": "Bearer secret",
            "apiKey": "secret",
            "profile": "完整画像原文",
            "nested": {"token": "secret"},
        },
    }

    first = build_bundle(documents=[document])
    second = build_bundle(documents=[copy.deepcopy(document)])
    first_source = first["evidenceChain"]["sources"][0]
    second_source = second["evidenceChain"]["sources"][0]

    assert first_source["evidenceId"] == second_source["evidenceId"]
    assert first_source["contentDigest"] == second_source["contentDigest"]
    assert first_source["contentDigest"].startswith("sha256:")
    serialized = json.dumps(first, ensure_ascii=False).lower()
    for forbidden in ("authorization", "apikey", "bearer secret", "完整画像原文", '"token"'):
        assert forbidden not in serialized


def test_learning_resource_metadata_uses_exact_public_allowlist():
    bundle = build_bundle(
        metadata={
            "courseKey": "python",
            "knowledgePoint": "loops",
            "learningPathId": "path-1",
            "learningPathItemKey": "loop-basics",
            "resourceKind": "practice_set",
            "reviewStatus": "passed",
            "userId": 7,
            "profile": {"level": "beginner"},
            "authorization": "Bearer secret",
            "internalPrompt": "drop-me",
        }
    )

    assert bundle["resources"][0]["metadata"] == {
        "courseKey": "python",
        "knowledgePoint": "loops",
        "learningPathId": "path-1",
        "learningPathItemKey": "loop-basics",
        "resourceKind": "practice_set",
        "reviewStatus": "passed",
    }
    serialized = json.dumps(bundle, ensure_ascii=False).lower()
    for forbidden in ("userid", "authorization", "bearer secret", "internalprompt"):
        assert forbidden not in serialized


def test_positional_tool_document_ids_do_not_become_persistent_evidence_ids():
    first = build_bundle(
        documents=[{"id": "tool:0", "content": "相同业务记录", "source": "java_backend"}]
    )
    reordered = build_bundle(
        documents=[{"id": "tool:9", "content": "相同业务记录", "source": "java_backend"}]
    )

    first_source = first["evidenceChain"]["sources"][0]
    reordered_source = reordered["evidenceChain"]["sources"][0]
    assert first_source["sourceId"] == reordered_source["sourceId"]
    assert first_source["evidenceId"] == reordered_source["evidenceId"]
    assert not first_source["sourceId"].startswith("tool:")


@pytest.mark.parametrize(
    ("documents", "request_context", "expected"),
    [
        ([{"id": "doc-1", "content": "事实", "source": "knowledge_base"}], {}, "grounded"),
        ([], {"historyContextUsed": True}, "context_only"),
        ([], {"profileContextUsed": True}, "context_only"),
        ([], {"currentPrompt": "当前问题不算上下文"}, "model_only"),
    ],
)
def test_grounding_states_are_mutually_exclusive(documents, request_context, expected):
    bundle = build_bundle(documents=documents, request_context=request_context)

    assert bundle["evidenceChain"]["status"] == expected
    assert {resource["groundingStatus"] for resource in bundle["resources"]} == {expected}
    assert expected in {"grounded", "context_only", "model_only"}


def test_sources_are_capped_at_twenty_and_excerpts_at_eight_hundred_characters():
    documents = [
        {"id": f"doc-{index}", "content": str(index) + ("知" * 1200), "source": "knowledge_base"}
        for index in range(25)
    ]

    bundle = build_bundle(documents=documents)
    sources = bundle["evidenceChain"]["sources"]

    assert len(sources) == 20
    assert all(len(source["excerpt"]) <= 800 for source in sources)
    assert [source["sourceId"] for source in sources] == [f"doc-{index}" for index in range(20)]


def test_bundle_respects_envelope_byte_cap_even_for_oversized_content():
    bundle = build_bundle(
        answer="答" * (MAX_ENVELOPE_BYTES * 2),
        metadata={"debug": "x" * (MAX_ENVELOPE_BYTES * 2)},
    )

    encoded = json.dumps(bundle, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    assert len(encoded) <= MAX_ENVELOPE_BYTES


def test_resource_and_evidence_links_are_exactly_bidirectional():
    bundle = build_bundle(
        documents=[
            {
                "id": "course:42",
                "content": "数据结构课程在周二第一至二节",
                "source": "java_backend",
                "metadata": {"kind": "course", "item": BUSINESS_CASES["course"][0]},
            }
        ]
    )

    resources = {resource["id"]: set(resource["evidenceIds"]) for resource in bundle["resources"]}
    links = {
        link["resourceId"]: set(link["evidenceIds"])
        for link in bundle["evidenceChain"]["resourceLinks"]
    }
    assert links == resources
    assert all(links.values())


def test_integrity_digest_verifies_and_detects_rewrites():
    bundle = build_bundle(
        documents=[{"id": "doc-1", "content": "可信事实", "source": "knowledge_base"}]
    )
    chain = bundle["evidenceChain"]

    assert verify_evidence_integrity(chain) is True
    tampered = copy.deepcopy(chain)
    tampered["sources"][0]["excerpt"] = "被改写"
    assert verify_evidence_integrity(tampered) is False


@pytest.mark.parametrize(
    "unsafe_url",
    [
        "http://localhost/private.png",
        "http://localhost.:8000/private.png",
        "http://service.local/private.png",
        "http://service.internal/private.png",
        "http://service.localhost/private.png",
        "http://127.0.0.1/private.png",
        "http://0.0.0.0/private.png",
        "http://169.254.169.254/private.png",
        "http://10.0.0.1/private.png",
        "http://172.16.0.1/private.png",
        "http://172.31.255.255/private.png",
        "http://192.168.0.1/private.png",
        "http://[::1]/private.png",
        "http://[::]/private.png",
        "http://[fc00::1]/private.png",
        "http://[fd12::1]/private.png",
        "http://[fe80::1]/private.png",
        "https://user:password@cdn.example.edu/private.png",
        "https://user@cdn.example.edu/private.png",
        "http://rag-service:8000/x",
        "http://python/x",
        "/internal/rag/x",
        "//internal-host/x",
    ],
)
def test_public_envelope_strips_private_or_credentialed_urls(unsafe_url):
    bundle = build_bundle(
        answer="",
        attachments=[{"name": "private.png", "type": "image", "url": unsafe_url}],
    )

    assert bundle["resources"][0]["url"] == ""


def test_public_envelope_keeps_public_http_urls_and_filters_business_card_urls():
    allowed_urls = (
        "/api/ai/resources/public.png",
        "/uploads/public.png",
        "https://cdn.example.edu/public.png",
        "http://8.8.8.8/public.png",
        "http://[2606:4700:4700::1111]/public.png",
    )
    for public_url in allowed_urls:
        attachment_bundle = build_bundle(
            answer="",
            attachments=[{"name": "public.png", "type": "image", "url": public_url}],
        )
        assert attachment_bundle["resources"][0]["url"] == public_url

    dining_item = copy.deepcopy(BUSINESS_CASES["dining"][0])
    dining_item["imageUrl"] = "http://menu.internal/private.png"
    business_bundle = build_bundle(
        documents=[{
            "id": "dining:3",
            "content": "食堂公开信息",
            "source": "java_backend",
            "metadata": {"kind": "dining", "item": dining_item},
        }]
    )

    dining_card = next(item for item in business_bundle["resources"] if item["kind"] == "dining")
    assert "imageUrl" not in dining_card["payload"]


def test_bundle_integrity_rejects_resource_side_link_tampering():
    bundle = build_bundle(
        documents=[{"id": "doc-1", "content": "可信事实", "source": "knowledge_base"}]
    )

    assert resource_builder.verify_assistant_resource_bundle(bundle) is True
    bundle["resources"][0]["evidenceIds"] = ["ev_tampered"]
    assert verify_evidence_integrity(bundle["evidenceChain"]) is True
    assert resource_builder.verify_assistant_resource_bundle(bundle) is False


def test_bundle_integrity_rejects_chain_side_link_tampering_after_valid_digest():
    bundle = build_bundle(
        documents=[{"id": "doc-1", "content": "可信事实", "source": "knowledge_base"}]
    )
    chain = bundle["evidenceChain"]
    chain["resourceLinks"][0]["evidenceIds"] = ["ev_tampered"]
    unsigned_chain = copy.deepcopy(chain)
    unsigned_chain.pop("integrity")
    chain["integrity"]["digest"] = "sha256:" + hashlib.sha256(
        resource_builder.canonical_json(unsigned_chain).encode("utf-8")
    ).hexdigest()

    assert verify_evidence_integrity(chain) is True
    assert resource_builder.verify_assistant_resource_bundle(bundle) is False
