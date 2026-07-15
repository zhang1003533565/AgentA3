#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import os
import re
import statistics
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set, Tuple


ANSWER_TYPES = {"deterministic", "rubric", "refusal"}
EXPECTED_DISTRIBUTION = {"deterministic": 20, "rubric": 5, "refusal": 5}
REQUIRED_FIELDS = {
    "id",
    "query",
    "expectedEvidence",
    "answerType",
    "expectedAnswer",
    "shouldRefuse",
}
METRIC_NAMES = [
    "recallAt5",
    "citationValidity",
    "deterministicAccuracy",
    "rubricPassCount",
    "refusalAccuracy",
    "errorRate",
    "p95LatencyMs",
]


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_gold_records(path: Path) -> List[Dict[str, Any]]:
    records: List[Dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"gold.jsonl line {line_number} is invalid JSON: {exc}") from exc
        if not isinstance(value, dict):
            raise ValueError(f"gold.jsonl line {line_number} must be an object")
        records.append(value)
    return records


def validate_gold_records(records: Sequence[Dict[str, Any]]) -> List[str]:
    errors: List[str] = []
    if len(records) != 30:
        errors.append(f"gold set must contain exactly 30 records; got {len(records)}")
    ids: List[str] = []
    distribution: Counter[str] = Counter()
    for index, record in enumerate(records, start=1):
        missing = sorted(REQUIRED_FIELDS - set(record))
        if missing:
            errors.append(f"record {index} missing fields: {', '.join(missing)}")
        record_id = str(record.get("id") or "").strip()
        if not record_id:
            errors.append(f"record {index} has blank id")
        ids.append(record_id)
        query = str(record.get("query") or "").strip()
        if not query:
            errors.append(f"record {record_id or index} has blank query")
        answer_type = str(record.get("answerType") or "")
        distribution[answer_type] += 1
        if answer_type not in ANSWER_TYPES:
            errors.append(f"record {record_id or index} has unknown answerType: {answer_type}")
        if not isinstance(record.get("expectedEvidence"), list):
            errors.append(f"record {record_id or index} expectedEvidence must be an array")
        if not isinstance(record.get("shouldRefuse"), bool):
            errors.append(f"record {record_id or index} shouldRefuse must be boolean")
        if answer_type == "refusal" and record.get("shouldRefuse") is not True:
            errors.append(f"record {record_id or index} refusal item must set shouldRefuse=true")
        if answer_type != "refusal" and record.get("shouldRefuse") is True:
            errors.append(f"record {record_id or index} answerable item cannot set shouldRefuse=true")
        if answer_type == "rubric":
            rubric = record.get("rubric")
            keyword_groups = record.get("rubricKeywords")
            if not isinstance(rubric, list) or not rubric:
                errors.append(f"record {record_id or index} rubric item requires rubric criteria")
            if not isinstance(keyword_groups, list) or not keyword_groups:
                errors.append(f"record {record_id or index} rubric item requires rubricKeywords")
    duplicates = sorted(item for item, count in Counter(ids).items() if item and count > 1)
    if duplicates:
        errors.append(f"duplicate record ids: {', '.join(duplicates)}")
    for answer_type, expected_count in EXPECTED_DISTRIBUTION.items():
        actual_count = distribution.get(answer_type, 0)
        if actual_count != expected_count:
            errors.append(
                f"{answer_type} distribution must be {expected_count}; got {actual_count}"
            )
    return errors


def _dataset_summary(records: Sequence[Dict[str, Any]], gold_hash: Optional[str] = None) -> Dict[str, Any]:
    counts = Counter(str(record.get("answerType") or "") for record in records)
    return {
        "total": len(records),
        "byType": {key: counts.get(key, 0) for key in sorted(ANSWER_TYPES)},
        "sha256": gold_hash,
    }


def build_not_run_report(records: Sequence[Dict[str, Any]], reason: str, gold_hash: Optional[str] = None) -> Dict[str, Any]:
    return {
        "schemaVersion": 1,
        "status": "not_run",
        "reason": reason,
        "attemptedAt": None,
        "dataset": _dataset_summary(records, gold_hash),
        "metrics": {name: None for name in METRIC_NAMES},
        "metricNotes": {
            "recallAt5": "Requires frozen expectedEvidence mapped to declared knowledge sources.",
            "citationValidity": "Requires a ready knowledge manifest and live returned citations.",
        },
        "results": [],
    }


def _normalize_text(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or "")).lower()


def _expected_answers(record: Dict[str, Any]) -> List[str]:
    value = record.get("expectedAnswer")
    if isinstance(value, list):
        return [str(item) for item in value if str(item).strip()]
    return [str(value)] if str(value or "").strip() else []


def _answer_matches(record: Dict[str, Any], answer: str) -> bool:
    normalized_answer = _normalize_text(answer)
    return any(
        _normalize_text(expected) in normalized_answer
        for expected in _expected_answers(record)
        if _normalize_text(expected)
    )


def _rubric_passes(record: Dict[str, Any], answer: str) -> bool:
    normalized = _normalize_text(answer)
    groups = record.get("rubricKeywords")
    if not isinstance(groups, list) or not groups:
        return False
    for raw_group in groups:
        terms = raw_group if isinstance(raw_group, list) else [raw_group]
        if not any(_normalize_text(term) in normalized for term in terms if _normalize_text(term)):
            return False
    return True


def _is_refusal(answer: str) -> bool:
    text = str(answer or "")
    signals = [
        r"资料不足",
        r"未提供",
        r"无法(?:确认|判断|访问|回答|提供|预测)",
        r"不能(?:确认|访问|提供|泄露|预测)",
        r"没有(?:足够|相关)资料",
        r"超出.*(?:知识|课程|资料)",
        r"请(?:上传|提供|补充)",
        r"无权访问",
    ]
    return any(re.search(pattern, text, flags=re.IGNORECASE) for pattern in signals)


def _collect_evidence_ids(value: Any) -> List[str]:
    found: List[str] = []

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            for key in ["sourceId", "source_id", "evidenceId", "evidence_id", "documentId", "id"]:
                candidate = node.get(key)
                if isinstance(candidate, (str, int)) and str(candidate).strip():
                    found.append(str(candidate).strip())
                    break
            for child_key in ["matchedResults", "references", "sources", "steps", "evidence"]:
                if child_key in node:
                    walk(node[child_key])
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(value)
    return list(dict.fromkeys(found))


def _expected_evidence_ids(record: Dict[str, Any]) -> List[str]:
    result: List[str] = []
    value = record.get("expectedEvidence")
    if not isinstance(value, list):
        return result
    for item in value:
        if isinstance(item, str) and item.strip():
            result.append(item.strip())
        elif isinstance(item, dict):
            source_id = str(item.get("sourceId") or item.get("source_id") or "").strip()
            if source_id:
                result.append(source_id)
    return result


def _extract_response(raw: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
    if raw.get("code") not in (None, 200):
        raise ValueError(str(raw.get("msg") or f"business code {raw.get('code')}"))
    data = raw.get("data", raw)
    if not isinstance(data, dict):
        raise ValueError("response data must be an object")
    return str(data.get("answer") or "").strip(), data


def _request_one(endpoint: str, token: str, record: Dict[str, Any], timeout: float) -> Dict[str, Any]:
    payload = {
        "sessionId": f"factual-{record['id']}-{uuid.uuid4().hex[:8]}",
        "input": record["query"],
    }
    authorization = token if token.lower().startswith("bearer ") else f"Bearer {token}"
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": authorization,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw_bytes = response.read()
            status = response.status
    except urllib.error.HTTPError as exc:
        raw_bytes = exc.read()
        status = exc.code
    latency_ms = round((time.perf_counter() - started) * 1000, 3)
    body = raw_bytes.decode("utf-8", errors="replace")
    if status < 200 or status >= 300:
        raise RuntimeError(f"HTTP {status}: {body[:1000]}")
    try:
        raw = json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"invalid JSON response: {body[:1000]}") from exc
    if not isinstance(raw, dict):
        raise RuntimeError("response root must be an object")
    answer, data = _extract_response(raw)
    return {
        "latencyMs": latency_ms,
        "answer": answer,
        "evidenceIds": _collect_evidence_ids({
            "matchedResults": data.get("matchedResults"),
            "evidenceChain": data.get("evidenceChain"),
        }),
        "rawResponse": raw,
    }


def _load_declared_source_ids(manifest_path: Path) -> Set[str]:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return set()
    if not isinstance(manifest, dict) or manifest.get("status") != "ready":
        return set()
    raw = manifest.get("sourceIds")
    return {str(item).strip() for item in raw} if isinstance(raw, list) else set()


def _p95(values: Sequence[float]) -> Optional[float]:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, math.ceil(0.95 * len(ordered)) - 1)
    return round(float(ordered[index]), 3)


def run_live_evaluation(
    records: Sequence[Dict[str, Any]],
    *,
    endpoint: str,
    token: str,
    timeout: float,
    declared_source_ids: Set[str],
    gold_hash: str,
) -> Dict[str, Any]:
    started_at = _utc_now()
    results: List[Dict[str, Any]] = []
    for record in records:
        result: Dict[str, Any] = {
            "id": record["id"],
            "answerType": record["answerType"],
            "shouldRefuse": record["shouldRefuse"],
        }
        try:
            response = _request_one(endpoint, token, record, timeout)
            result.update(response)
            answer = response["answer"]
            if record["answerType"] == "deterministic":
                result["passed"] = _answer_matches(record, answer)
            elif record["answerType"] == "rubric":
                result["passed"] = _rubric_passes(record, answer)
            else:
                result["passed"] = _is_refusal(answer)
            expected_ids = _expected_evidence_ids(record)
            returned_top5 = response["evidenceIds"][:5]
            result["expectedEvidence"] = expected_ids
            result["recallAt5"] = (
                len(set(expected_ids) & set(returned_top5)) / len(set(expected_ids))
                if expected_ids else None
            )
        except Exception as exc:
            result.update({
                "error": f"{type(exc).__name__}: {exc}",
                "passed": False,
                "latencyMs": None,
                "answer": "",
                "evidenceIds": [],
                "rawResponse": None,
                "expectedEvidence": _expected_evidence_ids(record),
                "recallAt5": None,
            })
        results.append(result)

    errors = [item for item in results if item.get("error")]
    deterministic = [item for item in results if item["answerType"] == "deterministic"]
    rubric = [item for item in results if item["answerType"] == "rubric"]
    refusals = [item for item in results if item["answerType"] == "refusal"]
    recall_values = [item["recallAt5"] for item in results if item.get("recallAt5") is not None]
    returned_evidence = [
        evidence_id
        for item in results
        for evidence_id in item.get("evidenceIds", [])
    ]
    valid_evidence = [item for item in returned_evidence if item in declared_source_ids]
    latencies = [float(item["latencyMs"]) for item in results if item.get("latencyMs") is not None]
    metrics = {
        "recallAt5": round(statistics.fmean(recall_values), 6) if recall_values else None,
        "citationValidity": (
            round(len(valid_evidence) / len(returned_evidence), 6)
            if returned_evidence and declared_source_ids else None
        ),
        "deterministicAccuracy": round(
            sum(bool(item.get("passed")) for item in deterministic) / len(deterministic), 6
        ),
        "rubricPassCount": sum(bool(item.get("passed")) for item in rubric),
        "refusalAccuracy": round(
            sum(bool(item.get("passed")) for item in refusals) / len(refusals), 6
        ),
        "errorRate": round(len(errors) / len(records), 6),
        "p95LatencyMs": _p95(latencies),
    }
    return {
        "schemaVersion": 1,
        "status": "completed",
        "reason": None,
        "startedAt": started_at,
        "finishedAt": _utc_now(),
        "endpoint": endpoint,
        "dataset": _dataset_summary(records, gold_hash),
        "metrics": metrics,
        "metricNotes": {
            "recallAt5": None if recall_values else "No frozen expectedEvidence values were available.",
            "citationValidity": None if declared_source_ids else "Knowledge manifest is not ready.",
            "rubric": "Automated keyword-group precheck; final competition claim requires human rubric review.",
        },
        "results": results,
    }


def _write_report(path: Path, report: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Run the frozen 30-question Python-course factual evaluation")
    parser.add_argument("--gold", type=Path, default=root / "evaluation/python-course/gold.jsonl")
    parser.add_argument("--manifest", type=Path, default=root / "artifacts/knowledge-base/python-course/manifest.json")
    parser.add_argument("--output", type=Path, default=root / "artifacts/verification/python-course-factual.json")
    parser.add_argument("--endpoint", default=os.getenv("EVAL_ENDPOINT", "").strip())
    parser.add_argument("--token", default=os.getenv("EVAL_TOKEN", "").strip())
    parser.add_argument("--timeout", type=float, default=float(os.getenv("EVAL_TIMEOUT_SECONDS", "90")))
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()

    try:
        records = load_gold_records(args.gold)
    except (OSError, ValueError) as exc:
        print(f"[FAIL] {exc}")
        return 1
    errors = validate_gold_records(records)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] gold.jsonl contains 20 deterministic, 5 rubric, and 5 refusal records")
    if args.validate_only:
        return 0

    gold_hash = _file_sha256(args.gold)
    if not args.endpoint or not args.token:
        report = build_not_run_report(
            records,
            "missing EVAL_ENDPOINT or EVAL_TOKEN; no live request was attempted",
            gold_hash,
        )
        _write_report(args.output, report)
        print(f"[NOT RUN] live factual evaluation requires EVAL_ENDPOINT and EVAL_TOKEN; wrote {args.output}")
        return 0

    report = run_live_evaluation(
        records,
        endpoint=args.endpoint,
        token=args.token,
        timeout=max(1.0, args.timeout),
        declared_source_ids=_load_declared_source_ids(args.manifest),
        gold_hash=gold_hash,
    )
    _write_report(args.output, report)
    print(json.dumps(report["metrics"], ensure_ascii=False, indent=2))
    print(f"[PASS] wrote raw factual evaluation results to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
