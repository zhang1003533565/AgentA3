from typing import List


class SemanticChunker:
    def __init__(self, chunk_size: int = 800, overlap: int = 120) -> None:
        self.chunk_size = chunk_size
        self.overlap = overlap

    def split(self, text: str) -> List[str]:
        if not text:
            return []
        normalized = text.strip()
        if len(normalized) <= self.chunk_size:
            return [normalized]

        chunks: List[str] = []
        start = 0
        while start < len(normalized):
            end = min(start + self.chunk_size, len(normalized))
            split_at = self._find_boundary(normalized, start, end)
            chunk = normalized[start:split_at].strip()
            if chunk:
                chunks.append(chunk)
            if split_at >= len(normalized):
                break
            start = max(split_at - self.overlap, start + 1)
        return chunks

    def _find_boundary(self, text: str, start: int, end: int) -> int:
        window = text[start:end]
        for marker in ("\n\n", "\n", "。", "！", "？", ".", "!", "?"):
            idx = window.rfind(marker)
            if idx > self.chunk_size * 0.45:
                return start + idx + len(marker)
        return end
