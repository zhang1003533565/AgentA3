import unittest

from run_load import build_not_run_report, nearest_rank_percentile, validate_load_plan


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


if __name__ == "__main__":
    unittest.main()
