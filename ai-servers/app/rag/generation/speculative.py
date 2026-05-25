class SpeculativeGenerator:
    def draft(self, query: str) -> str:
        return ""

    def revise(self, draft_answer: str, evidence: str) -> str:
        return draft_answer if draft_answer else evidence
