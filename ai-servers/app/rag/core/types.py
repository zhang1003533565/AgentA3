from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class RagQuery:
    text: str
    intent: str = "campus_search"
    keyword: str = ""
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RagDocument:
    id: str
    content: str
    source: str = ""
    score: Optional[float] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RagTraceStep:
    stage: str
    detail: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RagResult:
    strategy: str
    answer: str = ""
    documents: List[RagDocument] = field(default_factory=list)
    trace: List[RagTraceStep] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)
