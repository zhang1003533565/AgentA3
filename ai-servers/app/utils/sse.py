import json
from typing import Any, List


def build_sse(event: str, data: Any) -> str:
    payload = json.dumps(data, ensure_ascii=False)
    # SSE requires real LF separators, not literal backslash-n.
    return f"event: {event}\ndata: {payload}\n\n"


def chunk_answer(answer: str) -> List[str]:
    chunks: List[str] = []
    current: List[str] = []
    for ch in answer:
        current.append(ch)
        should_flush = len(current) >= 12 or ch in "，。！？；：,.!?;\\n"
        if should_flush:
            chunks.append("".join(current))
            current = []
    if current:
        chunks.append("".join(current))
    return chunks
