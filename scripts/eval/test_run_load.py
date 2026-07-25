import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from run_load import (
    _request_once,
    _write_report,
    build_not_run_report,
    nearest_rank_percentile,
    run_load_test,
    validate_load_plan,
)


class LoadEvaluationContractTest(unittest.TestCase):
    def test_competition_minimum_plan_is_accepted(self):
        self.assertEqual([], validate_load_plan(total_requests=50, concurrency=5, timeout_seconds=90))

    def test_plan_below_required_volume_is_rejected(self):
        errors = validate_load_plan(total_requests=49, concurrency=4, timeout_seconds=0)

        self.assertTrue(any("50" in error for error in errors))
        self.assertTrue(any("5" in error for error in errors))
        self.assertTrue(any("timeout" in error for error in errors))

    def test_nearest_rank_percentile_uses_observed_value(self):
        values = list(range(1, 101))

        self.assertEqual(50.0, nearest_rank_percentile(values, 0.50))
        self.assertEqual(95.0, nearest_rank_percentile(values, 0.95))
        self.assertEqual(99.0, nearest_rank_percentile(values, 0.99))

    def test_missing_credentials_produces_null_metrics(self):
        report = build_not_run_report(
            total_requests=50,
            concurrency=5,
            reason="missing LOAD_ENDPOINT or LOAD_TOKEN",
        )

        self.assertEqual("not_run", report["status"])
        self.assertEqual(50, report["plan"]["totalRequests"])
        self.assertEqual(5, report["plan"]["concurrency"])
        self.assertTrue(all(value is None for value in report["metrics"].values()))
        self.assertEqual([], report["requests"])

    @patch(
        "run_load.urllib.request.urlopen",
        side_effect=RuntimeError(
            'request failed: {"secret":"secret-json","password":"two word secret"}; '
            "Cookie: session=word secret"
        ),
    )
    def test_request_error_is_sanitized(self, _urlopen):
        result = _request_once(
            "https://user:pass@example.invalid/query?token=top-secret",
            "local-token",
            1,
            0,
            "query",
        )

        serialized = json.dumps(result, ensure_ascii=False)

        for secret in ["secret-json", "two word secret", "word secret", "top-secret"]:
            self.assertNotIn(secret, serialized)

    @patch("run_load._request_once")
    def test_report_endpoint_and_worker_errors_are_sanitized(self, request_once):
        request_once.return_value = {
            "index": 0,
            "success": False,
            "status": 500,
            "latencyMs": 1.0,
            "answerLength": 0,
            "answerSha256": None,
            "error": "password=two word secret; token=top-secret",
        }

        report = run_load_test(
            endpoint="https://user:pass@example.invalid/query?api_key=top-secret",
            token="local-token",
            queries=["query"],
            total_requests=1,
            concurrency=1,
            timeout_seconds=1,
            gold_hash="0" * 64,
        )
        serialized = json.dumps(report, ensure_ascii=False)

        self.assertNotIn("user:pass", serialized)
        self.assertNotIn("two word secret", serialized)
        self.assertNotIn("top-secret", serialized)

    def test_report_writer_sanitizes_stringified_json(self):
        report = {
            "endpoint": "https://user:pass@example.invalid/query?token=top-secret",
            "requests": [{
                "error": '{"secret":"secret-json","password":"two word secret"}',
                "headers": "Set-Cookie: session=word secret",
            }],
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "load.json"

            _write_report(output, report)

            serialized = output.read_text(encoding="utf-8")

        for secret in [
            "secret-json",
            "two word secret",
            "word secret",
            "user:pass",
            "top-secret",
        ]:
            self.assertNotIn(secret, serialized)


if __name__ == "__main__":
    unittest.main()
