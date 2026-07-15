import unittest
from unittest.mock import patch

from run_factual_eval import (
    THRESHOLDS,
    _redact_error,
    _redact_raw_response,
    build_not_run_report,
    evaluation_exit_code,
    metrics_pass_thresholds,
    run_live_evaluation,
    validate_gold_records,
)


def make_records():
    records = []
    for index in range(20):
        records.append({
            "id": f"det-{index}",
            "query": "query",
            "expectedEvidence": [],
            "answerType": "deterministic",
            "expectedAnswer": "answer",
            "shouldRefuse": False,
        })
    for index in range(5):
        records.append({
            "id": f"rubric-{index}",
            "query": "query",
            "expectedEvidence": [],
            "answerType": "rubric",
            "expectedAnswer": "answer",
            "rubric": ["criterion"],
            "rubricKeywords": [["keyword"]],
            "shouldRefuse": False,
        })
    for index in range(5):
        records.append({
            "id": f"refuse-{index}",
            "query": "query",
            "expectedEvidence": [],
            "answerType": "refusal",
            "expectedAnswer": "answer",
            "shouldRefuse": True,
        })
    return records


class FactualEvaluationContractTest(unittest.TestCase):
    def test_frozen_distribution_is_accepted(self):
        self.assertEqual([], validate_gold_records(make_records()))

    def test_duplicate_ids_and_wrong_distribution_are_rejected(self):
        records = make_records()[:-1]
        records[1]["id"] = records[0]["id"]

        errors = validate_gold_records(records)

        self.assertTrue(any("30" in error for error in errors))
        self.assertTrue(any("duplicate" in error for error in errors))
        self.assertTrue(any("refusal" in error for error in errors))

    def test_missing_live_credentials_produces_null_metrics(self):
        report = build_not_run_report(make_records(), "missing EVAL_ENDPOINT or EVAL_TOKEN")

        self.assertEqual("not_run", report["status"])
        self.assertIsNone(report["passed"])
        self.assertEqual(THRESHOLDS, report["thresholds"])
        self.assertEqual(30, report["dataset"]["total"])
        self.assertTrue(all(value is None for value in report["metrics"].values()))
        self.assertEqual([], report["results"])

    def test_thresholds_are_frozen_and_missing_or_below_threshold_metrics_fail(self):
        passing = {
            "recallAt5": 0.80,
            "citationValidity": 1.0,
            "deterministicAccuracy": 0.90,
            "rubricPassCount": 4,
            "refusalAccuracy": 0.80,
            "errorRate": 0.05,
            "p95LatencyMs": 30000,
        }

        self.assertTrue(metrics_pass_thresholds(passing))
        self.assertFalse(metrics_pass_thresholds({**passing, "recallAt5": 0.79}))
        self.assertFalse(metrics_pass_thresholds({**passing, "citationValidity": None}))

    @patch("run_factual_eval._request_one", side_effect=TimeoutError("all requests failed"))
    def test_all_failed_requests_complete_with_failed_gate_and_nonzero_exit(self, _request):
        records = make_records()
        for record in records:
            if record["answerType"] != "refusal":
                record["expectedEvidence"] = ["python-source"]

        report = run_live_evaluation(
            records,
            endpoint="http://evaluation.invalid/query",
            token="local-test-token",
            timeout=1,
            declared_source_ids={"python-source"},
            gold_hash="0" * 64,
        )

        self.assertEqual("completed", report["status"])
        self.assertFalse(report["passed"])
        self.assertEqual(1.0, report["metrics"]["errorRate"])
        self.assertEqual(1, evaluation_exit_code(report))
        self.assertTrue(all(result["passed"] is False for result in report["results"]))

    def test_raw_response_redaction_is_recursive_and_deterministic(self):
        raw = {
            "data": {
                "answer": "可公开答案",
                "sessionId": "factual-det-1-random",
                "apiKey": "secret-key",
                "nested": {
                    "authorization": "Bearer highly-sensitive",
                    "resourceDownloadCapability": "capability-secret",
                    "url": "https://example.invalid/callback?token=secret&safe=ok",
                },
            }
        }

        first = _redact_raw_response(raw)
        second = _redact_raw_response(raw)

        self.assertEqual(first, second)
        self.assertEqual("可公开答案", first["data"]["answer"])
        self.assertEqual("[REDACTED]", first["data"]["sessionId"])
        self.assertEqual("[REDACTED]", first["data"]["apiKey"])
        self.assertEqual("[REDACTED]", first["data"]["nested"]["authorization"])
        self.assertEqual("[REDACTED]", first["data"]["nested"]["resourceDownloadCapability"])
        self.assertNotIn("secret", first["data"]["nested"]["url"])

    def test_error_redaction_removes_json_and_key_value_credentials(self):
        error = 'HTTP 500: {"apiKey":"secret-json","password": plain-secret}'

        redacted = _redact_error(error)

        self.assertNotIn("secret-json", redacted)
        self.assertNotIn("plain-secret", redacted)


if __name__ == "__main__":
    unittest.main()
