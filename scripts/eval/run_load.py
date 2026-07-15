#!/usr/bin/env python3
import argparse
import concurrent.futures
import hashlib
import json
import math
import os
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence


MIN_REQUESTS = 50
MIN_CONCURRENCY = 5
METRIC_NAMES = [
    "throughputRps",
    "successRate",
    "errorRate",
    "p50LatencyMs",
    "p95LatencyMs",
    "p99LatencyMs",
    "maxLatencyMs",
]
THRESHOLDS = {
    "minSuccessRate": 0.95,
    "maxErrorRate": 0.05,
    "maxP95LatencyMs": 60000,
}


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def validate_load_plan(total_requests: int, concurrency: int, timeout_seconds: float) -> List[str]:
    errors: List[str] = []
    if total_requests < MIN_REQUESTS:
        errors.append(f"total requests must be at least {MIN_REQUESTS}; got {total_requests}")
    if concurrency < MIN_CONCURRENCY:
        errors.append(f"concurrency must be at least {MIN_CONCURRENCY}; got {concurrency}")
    if concurrency > total_requests:
        errors.append("concurrency cannot exceed total requests")
    if timeout_seconds <= 0:
        errors.append("timeout must be greater than zero")
    return errors


def nearest_rank_percentile(values: Sequence[float], percentile: float) -> Optional[float]:
    if not values:
        return None
    ordered = sorted(float(value) for value in values)
    rank = max(1, math.ceil(percentile * len(ordered)))
    return round(ordered[rank - 1], 3)


def build_not_run_report(
    *,
    total_requests: int,
    concurrency: int,
    reason: str,
    gold_hash: Optional[str] = None,
) -> Dict[str, Any]:
    return {
        "schemaVersion": 1,
        "status": "not_run",
        "passed": None,
        "reason": reason,
        "attemptedAt": None,
        "plan": {
            "totalRequests": total_requests,
            "concurrency": concurrency,
            "goldSha256": gold_hash,
        },
        "thresholds": THRESHOLDS,
        "metrics": {name: None for name in METRIC_NAMES},
        "statusCounts": {},
        "requests": [],
    }


def _load_queries(path: Path) -> List[str]:
    queries: List[str] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"gold.jsonl line {line_number} is invalid JSON: {exc}") from exc
        if not isinstance(record, dict):
            raise ValueError(f"gold.jsonl line {line_number} must be an object")
        if record.get("shouldRefuse") is False:
            query = str(record.get("query") or "").strip()
            if query:
                queries.append(query)
    if not queries:
        raise ValueError("gold.jsonl has no answerable queries for load testing")
    return queries


def _request_once(
    endpoint: str,
    token: str,
    timeout_seconds: float,
    request_index: int,
    query: str,
) -> Dict[str, Any]:
    payload = {
        "sessionId": f"load-{request_index:03d}-{uuid.uuid4().hex[:8]}",
        "input": query,
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
    status = 0
    body = b""
    try:
        try:
            with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
                status = int(response.status)
                body = response.read()
        except urllib.error.HTTPError as exc:
            status = int(exc.code)
            body = exc.read()
        latency_ms = round((time.perf_counter() - started) * 1000, 3)
        decoded = body.decode("utf-8", errors="replace")
        if status < 200 or status >= 300:
            raise RuntimeError(f"HTTP {status}: {decoded[:500]}")
        try:
            raw = json.loads(decoded)
        except json.JSONDecodeError as exc:
            raise RuntimeError(f"invalid JSON: {decoded[:500]}") from exc
        if not isinstance(raw, dict):
            raise RuntimeError("response root must be an object")
        if raw.get("code") not in (None, 200):
            raise RuntimeError(str(raw.get("msg") or f"business code {raw.get('code')}"))
        data = raw.get("data", raw)
        if not isinstance(data, dict):
            raise RuntimeError("response data must be an object")
        answer = str(data.get("answer") or "").strip()
        if not answer:
            raise RuntimeError("response answer is blank")
        return {
            "index": request_index,
            "success": True,
            "status": status,
            "latencyMs": latency_ms,
            "answerLength": len(answer),
            "answerSha256": hashlib.sha256(answer.encode("utf-8")).hexdigest(),
            "error": None,
        }
    except Exception as exc:
        latency_ms = round((time.perf_counter() - started) * 1000, 3)
        return {
            "index": request_index,
            "success": False,
            "status": status,
            "latencyMs": latency_ms,
            "answerLength": 0,
            "answerSha256": None,
            "error": f"{type(exc).__name__}: {exc}",
        }


def run_load_test(
    *,
    endpoint: str,
    token: str,
    queries: Sequence[str],
    total_requests: int,
    concurrency: int,
    timeout_seconds: float,
    gold_hash: str,
) -> Dict[str, Any]:
    started_at = _utc_now()
    wall_started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(
                _request_once,
                endpoint,
                token,
                timeout_seconds,
                index,
                queries[index % len(queries)],
            )
            for index in range(total_requests)
        ]
        results = [future.result() for future in concurrent.futures.as_completed(futures)]
    wall_seconds = max(time.perf_counter() - wall_started, 0.000001)
    results.sort(key=lambda item: item["index"])
    successes = sum(bool(item["success"]) for item in results)
    errors = total_requests - successes
    latencies = [float(item["latencyMs"]) for item in results]
    success_rate = successes / total_requests
    error_rate = errors / total_requests
    metrics = {
        "throughputRps": round(total_requests / wall_seconds, 6),
        "successRate": round(success_rate, 6),
        "errorRate": round(error_rate, 6),
        "p50LatencyMs": nearest_rank_percentile(latencies, 0.50),
        "p95LatencyMs": nearest_rank_percentile(latencies, 0.95),
        "p99LatencyMs": nearest_rank_percentile(latencies, 0.99),
        "maxLatencyMs": round(max(latencies), 3) if latencies else None,
    }
    passed = (
        success_rate >= THRESHOLDS["minSuccessRate"]
        and error_rate <= THRESHOLDS["maxErrorRate"]
        and metrics["p95LatencyMs"] is not None
        and metrics["p95LatencyMs"] <= THRESHOLDS["maxP95LatencyMs"]
    )
    status_counts = Counter(str(item["status"]) for item in results)
    return {
        "schemaVersion": 1,
        "status": "completed",
        "passed": passed,
        "reason": None,
        "startedAt": started_at,
        "finishedAt": _utc_now(),
        "endpoint": endpoint,
        "plan": {
            "totalRequests": total_requests,
            "concurrency": concurrency,
            "timeoutSeconds": timeout_seconds,
            "goldSha256": gold_hash,
        },
        "thresholds": THRESHOLDS,
        "metrics": metrics,
        "statusCounts": dict(sorted(status_counts.items())),
        "requests": results,
    }


def _write_report(path: Path, report: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Run the fixed 5-concurrency/50-request Python-course load test")
    parser.add_argument("--gold", type=Path, default=root / "evaluation/python-course/gold.jsonl")
    parser.add_argument("--output", type=Path, default=root / "artifacts/verification/python-course-load.json")
    parser.add_argument("--endpoint", default=(os.getenv("LOAD_ENDPOINT") or os.getenv("EVAL_ENDPOINT") or "").strip())
    parser.add_argument("--token", default=(os.getenv("LOAD_TOKEN") or os.getenv("EVAL_TOKEN") or "").strip())
    parser.add_argument("--requests", type=int, default=int(os.getenv("LOAD_REQUESTS", "50")))
    parser.add_argument("--concurrency", type=int, default=int(os.getenv("LOAD_CONCURRENCY", "5")))
    parser.add_argument("--timeout", type=float, default=float(os.getenv("LOAD_TIMEOUT_SECONDS", "120")))
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()

    plan_errors = validate_load_plan(args.requests, args.concurrency, args.timeout)
    if plan_errors:
        for error in plan_errors:
            print(f"[FAIL] {error}")
        return 1
    try:
        queries = _load_queries(args.gold)
    except (OSError, ValueError) as exc:
        print(f"[FAIL] {exc}")
        return 1
    gold_hash = hashlib.sha256(args.gold.read_bytes()).hexdigest()
    print(
        f"[PASS] load plan uses {args.concurrency} workers, {args.requests} requests, "
        f"and {len(queries)} frozen answerable queries"
    )
    if args.validate_only:
        return 0
    if not args.endpoint or not args.token:
        report = build_not_run_report(
            total_requests=args.requests,
            concurrency=args.concurrency,
            reason="missing LOAD_ENDPOINT or LOAD_TOKEN; no live request was attempted",
            gold_hash=gold_hash,
        )
        _write_report(args.output, report)
        print(f"[NOT RUN] live load test requires LOAD_ENDPOINT and LOAD_TOKEN; wrote {args.output}")
        return 0

    report = run_load_test(
        endpoint=args.endpoint,
        token=args.token,
        queries=queries,
        total_requests=args.requests,
        concurrency=args.concurrency,
        timeout_seconds=args.timeout,
        gold_hash=gold_hash,
    )
    _write_report(args.output, report)
    print(json.dumps(report["metrics"], ensure_ascii=False, indent=2))
    if report["passed"]:
        print(f"[PASS] load thresholds passed; wrote {args.output}")
        return 0
    print(f"[FAIL] load thresholds were not met; wrote {args.output}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
