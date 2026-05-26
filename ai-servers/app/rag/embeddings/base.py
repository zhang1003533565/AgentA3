from abc import ABC, abstractmethod
from typing import Any, Dict, List

EmbeddingVector = Dict[str, float]


class BaseEmbeddingProvider(ABC):
    name = "base"
    status = "implemented"
    dimension = "sparse"

    @abstractmethod
    def embed_text(self, text: str) -> EmbeddingVector:
        raise NotImplementedError

    def embed_query(self, query: str) -> EmbeddingVector:
        return self.embed_text(query)

    def embed_texts(self, texts: List[str]) -> List[EmbeddingVector]:
        return [self.embed_text(text) for text in texts]

    def health(self) -> Dict[str, Any]:
        return {
            "provider": self.name,
            "status": self.status,
            "dimension": self.dimension,
            "configured": True,
        }
