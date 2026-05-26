class SpeculativeGenerator:
    def draft(self, query: str) -> str:
        normalized = (query or "").strip()
        if not normalized:
            return ""
        return f"先基于问题生成一个待验证草稿：{normalized}"

    def revise(self, draft_answer: str, evidence: str) -> str:
        draft = (draft_answer or "").strip()
        normalized_evidence = (evidence or "").strip()
        if draft and normalized_evidence:
            return f"{draft}\n\n已根据检索证据修订：{normalized_evidence[:600]}"
        return draft or normalized_evidence
