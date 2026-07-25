import unittest
from fastapi import HTTPException

from app.api.routes.rag import _validate_rag_input_length
from app.models.schemas import RagQueryRequest


class RagInputLimitsTest(unittest.TestCase):
    def test_question_generation_accepts_complete_material_over_default_limit(self):
        request = RagQueryRequest(
            input="知" * 20_000,
            agentName="textbook_question_single_choice_agent",
            metadata={"requestPurpose": "question_generation"},
        )

        _validate_rag_input_length(request)

    def test_ordinary_rag_request_keeps_original_input_limit(self):
        request = RagQueryRequest(input="a" * 4_001, agentName="leader_agent")

        with self.assertRaises(HTTPException) as raised:
            _validate_rag_input_length(request)

        self.assertEqual(422, raised.exception.status_code)
        self.assertIn("4000", str(raised.exception.detail))


if __name__ == "__main__":
    unittest.main()
