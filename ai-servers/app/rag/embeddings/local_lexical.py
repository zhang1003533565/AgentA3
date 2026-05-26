import re
from collections import Counter
from typing import List

from app.rag.embeddings.base import BaseEmbeddingProvider, EmbeddingVector


class LocalLexicalEmbeddingProvider(BaseEmbeddingProvider):
    name = "local_lexical"
    dimension = "sparse_lexical"

    def embed_text(self, text: str) -> EmbeddingVector:
        counts = Counter(self._tokenize(text))
        return {token: float(count) for token, count in counts.items()}

    def _tokenize(self, text: str) -> List[str]:
        normalized = (text or "").lower()
        words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]", normalized)
        chinese_chars = [token for token in words if re.fullmatch(r"[\u4e00-\u9fff]", token)]
        bigrams = [a + b for a, b in zip(chinese_chars, chinese_chars[1:])]
        return words + bigrams
