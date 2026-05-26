import unittest

from app.rag.core.types import RagDocument
from app.rag.evaluators import RagEvaluationInput, RagEvaluator


class RagEvaluatorTest(unittest.TestCase):
    def test_evaluator_scores_hit_rate_mrr_and_faithfulness(self):
        result = RagEvaluator().evaluate(RagEvaluationInput(
            query="校园卡补办地点",
            answer="校园卡补办地点在行政楼一楼服务大厅。",
            documents=[
                RagDocument(id="card", content="校园卡补办地点在行政楼一楼服务大厅。", source="card.md"),
                RagDocument(id="sports", content="体育馆预约羽毛球。", source="sports.md"),
            ],
            expected_sources=["card.md"],
            expected_answer_terms=["行政楼"],
        ))

        self.assertTrue(result.passed)
        self.assertEqual(1.0, result.metrics["hitRate"])
        self.assertEqual(1.0, result.metrics["mrr"])
        self.assertGreater(result.metrics["contextRelevance"], 0)
        self.assertGreater(result.metrics["faithfulness"], 0)


if __name__ == "__main__":
    unittest.main()
