import unittest

from run_factual_eval import build_not_run_report, validate_gold_records


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
        self.assertEqual(30, report["dataset"]["total"])
        self.assertTrue(all(value is None for value in report["metrics"].values()))
        self.assertEqual([], report["results"])


if __name__ == "__main__":
    unittest.main()
