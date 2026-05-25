from dataclasses import dataclass
from typing import List


@dataclass
class ParentChildChunk:
    parent: str
    children: List[str]


class ParentChildChunker:
    def split(self, text: str) -> ParentChildChunk:
        return ParentChildChunk(parent=text, children=[text] if text else [])
