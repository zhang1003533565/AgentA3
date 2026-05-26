import importlib.util
import os
from typing import Any, Dict, List

from app.rag.embeddings.base import BaseEmbeddingProvider, EmbeddingVector


class ScaffoldedEmbeddingProvider(BaseEmbeddingProvider):
    name = "scaffolded"
    status = "scaffolded"
    dependency = ""
    dependency_import = ""
    required_env: List[str] = []
    optional_env: List[str] = []
    description = "Embedding provider scaffold. Runtime integration is not implemented yet."

    def embed_text(self, text: str) -> EmbeddingVector:
        raise RuntimeError(
            f"{self.name} embedding provider is not available. "
            "Check provider dependency and required environment variables."
        )

    def health(self) -> Dict[str, Any]:
        missing_env = [key for key in self.required_env if not os.getenv(key)]
        dependency_available = self._dependency_available()
        return {
            "provider": self.name,
            "status": self.status,
            "description": self.description,
            "dependency": self.dependency,
            "dependencyAvailable": dependency_available,
            "configured": not missing_env and dependency_available,
            "requiredEnv": self.required_env,
            "optionalEnv": self.optional_env,
            "missingEnv": missing_env,
            "dimension": self.dimension,
        }

    def _dependency_available(self) -> bool:
        if not self.dependency_import:
            return True
        return importlib.util.find_spec(self.dependency_import) is not None

    def _dense_to_embedding_vector(self, values: List[float]) -> EmbeddingVector:
        return {str(index): float(value) for index, value in enumerate(values)}
