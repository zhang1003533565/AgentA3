from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    sessionId: Optional[str] = Field(default=None, max_length=64)
    prompt: Optional[str] = Field(default=None, max_length=2000)
    ragStrategy: Optional[str] = Field(default="naive_rag", max_length=64)
    input: str = Field(min_length=1, max_length=4000)


class ChatResponse(BaseModel):
    sessionId: str
    sessionToken: str
    model: str
    ragStrategy: str = "naive_rag"
    searchKeyword: str
    matchedResults: List[Dict[str, Any]]
    retrievalMeta: Dict[str, Any] = Field(default_factory=dict)
    trace: List[Dict[str, Any]] = Field(default_factory=list)
    answer: str


class RagQueryRequest(BaseModel):
    input: str = Field(min_length=1, max_length=4000)
    keyword: Optional[str] = Field(default=None, max_length=128)
    intent: str = Field(default="campus_search", max_length=64)
    ragStrategy: Optional[str] = Field(default="naive_rag", max_length=64)
    metadata: Dict[str, Any] = Field(default_factory=dict)


class RagDocumentResponse(BaseModel):
    id: str
    content: str
    source: str = ""
    score: Optional[float] = None
    metadata: Dict[str, Any] = Field(default_factory=dict)


class RagTraceResponse(BaseModel):
    stage: str
    detail: Dict[str, Any] = Field(default_factory=dict)


class RagQueryResponse(BaseModel):
    strategy: str
    answer: str = ""
    documents: List[RagDocumentResponse] = Field(default_factory=list)
    trace: List[RagTraceResponse] = Field(default_factory=list)
    metadata: Dict[str, Any] = Field(default_factory=dict)


class RagDocumentIngestItem(BaseModel):
    content: str = Field(min_length=1, max_length=200000)
    source: Optional[str] = Field(default=None, max_length=256)
    metadata: Dict[str, Any] = Field(default_factory=dict)


class RagDocumentIngestRequest(BaseModel):
    documents: List[RagDocumentIngestItem] = Field(min_length=1, max_length=50)


class RagDocumentIngestResponse(BaseModel):
    storedCount: int
    storedFiles: List[str]
    indexedChunkCount: int = 0
    indexPath: str = ""
    documents: List[Dict[str, Any]] = Field(default_factory=list)
    trace: List[RagTraceResponse] = Field(default_factory=list)
