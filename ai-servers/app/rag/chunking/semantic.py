from typing import List


class SemanticChunker:
    def split(self, text: str) -> List[str]:
        return [text] if text else []
