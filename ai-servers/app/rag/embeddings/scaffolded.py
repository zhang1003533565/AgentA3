import os
from typing import Any, Dict, List

from app.rag.embeddings.base import BaseEmbeddingProvider, EmbeddingVector


class ScaffoldedEmbeddingProvider(BaseEmbeddingProvider):
    name = "scaffolded"
    status = "scaffolded"
    dependency = ""
    required_env: List[str] = []
    optional_env: List[str] = []
    description = "Embedding provider scaffold. Runtime integration is not implemented yet."

    def embed_text(self, text: str) -> EmbeddingVector:
        raise RuntimeError(
            f"{self.name} embedding provider is scaffolded only. "
            "Use local_lexical or finish this provider before enabling it."
        )

    def health(self) -> Dict[str, Any]:
        missing_env = [key for key in self.required_env if not os.getenv(key)]
        return {
            "provider": self.name,
            "status": self.status,
            "description": self.description,
            "dependency": self.dependency,
            "configured": not missing_env,
            "requiredEnv": self.required_env,
            "optionalEnv": self.optional_env,
            "missingEnv": missing_env,
            "dimension": self.dimension,
        }
