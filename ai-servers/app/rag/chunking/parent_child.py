from dataclasses import dataclass
from typing import List

from app.rag.chunking.semantic import SemanticChunker


@dataclass
class ParentChildChunk:
    parent_id: str
    parent: str
    children: List[str]


class ParentChildChunker:
    def __init__(
        self,
        parent_chunk_size: int = 1600,
        parent_overlap: int = 160,
        child_chunk_size: int = 420,
        child_overlap: int = 80,
    ) -> None:
        self.parent_chunker = SemanticChunker(parent_chunk_size, parent_overlap)
        self.child_chunker = SemanticChunker(child_chunk_size, child_overlap)

    def split(self, text: str) -> List[ParentChildChunk]:
        parents = self.parent_chunker.split(text)
        chunks: List[ParentChildChunk] = []
        for index, parent in enumerate(parents):
            chunks.append(ParentChildChunk(
                parent_id=str(index),
                parent=parent,
                children=self.child_chunker.split(parent),
            ))
        return chunks
