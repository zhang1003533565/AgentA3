import base64
import binascii
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field, model_validator


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
    content: str = Field(default="", max_length=200000)
    contentBase64: Optional[str] = Field(default=None, max_length=8_000_000)
    source: Optional[str] = Field(default=None, max_length=256)
    metadata: Dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_payload(self):
        if not self.content and not self.contentBase64:
            raise ValueError("文档内容或文件内容不能为空")
        if self.contentBase64:
            try:
                base64.b64decode(self.contentBase64, validate=True)
            except (ValueError, binascii.Error) as exc:
                raise ValueError("contentBase64 不是有效的 Base64 文件内容") from exc
        return self


class RagDocumentIngestRequest(BaseModel):
    documents: List[RagDocumentIngestItem] = Field(min_length=1, max_length=50)


class RagDocumentIngestResponse(BaseModel):
    storedCount: int
    storedFiles: List[str]
    indexedChunkCount: int = 0
    indexPath: str = ""
    documents: List[Dict[str, Any]] = Field(default_factory=list)
    trace: List[RagTraceResponse] = Field(default_factory=list)


class RagEvaluateRequest(BaseModel):
    query: str = Field(min_length=1, max_length=4000)
    answer: str = Field(default="", max_length=12000)
    documents: List[RagDocumentResponse] = Field(default_factory=list)
    expectedSources: List[str] = Field(default_factory=list)
    expectedAnswerTerms: List[str] = Field(default_factory=list)


class RagEvaluateResponse(BaseModel):
    metrics: Dict[str, float]
    passed: bool
    detail: Dict[str, Any] = Field(default_factory=dict)
