import re
from typing import List


class MultiQueryTransformer:
    def transform(self, query: str) -> List[str]:
        normalized = (query or "").strip()
        if not normalized:
            return []

        compact = re.sub(r"\s+", "", normalized)
        variants = [
            normalized,
            compact,
            self._remove_question_words(compact),
            f"{compact} 办理 地点 时间 流程",
            f"{compact} 校园 服务 指南",
        ]
        return self._unique([item for item in variants if item])

    def _remove_question_words(self, text: str) -> str:
        return re.sub(r"(请问|怎么|如何|哪里|在哪|什么|多少|吗|呢|啊|？|\\?)", "", text)

    def _unique(self, values: List[str]) -> List[str]:
        seen: set[str] = set()
        unique_values: List[str] = []
        for value in values:
            normalized = value.strip()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            unique_values.append(normalized)
        return unique_values
