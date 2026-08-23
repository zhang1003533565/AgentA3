from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    sessionId: Optional[str] = Field(default=None, max_length=64)
    prompt: Optional[str] = Field(default=None, max_length=2000)
    ragStrategy: Optional[str] = Field(default=None, max_length=64)
    agentName: Optional[str] = Field(default=None, max_length=64)
    input: str = Field(min_length=1, max_length=4000)
    imageUrls: List[str] = Field(default_factory=list, max_length=8)
    images: List[str] = Field(default_factory=list, max_length=8)
    imageDataUrls: List[str] = Field(default_factory=list, max_length=8)
    attachments: List[Dict[str, Any]] = Field(default_factory=list, max_length=8)


class CodingAssistRequest(BaseModel):
    sessionId: Optional[str] = Field(default=None, max_length=64)
    questionType: str = Field(pattern="^(hint|solution|explain|debug|optimize|free)$")
    problem: Dict[str, Any] = Field(default_factory=dict)
    userCode: Optional[str] = Field(default=None, max_length=30000)
    judgeResult: Optional[Dict[str, Any]] = Field(default=None)
    followUp: Optional[str] = Field(default=None, max_length=2000)
    history: List[Dict[str, Any]] = Field(default_factory=list, max_length=10)


class ChatResponse(BaseModel):
    sessionId: str
    sessionToken: str
    model: str
    ragStrategy: str = "direct_agent"
    agentName: str = "leader_agent"
    searchKeyword: str
    matchedResults: List[Dict[str, Any]]
    retrievalMeta: Dict[str, Any] = Field(default_factory=dict)
    trace: List[Dict[str, Any]] = Field(default_factory=list)
    answer: str
    answerType: str = "text"
    resources: List[Dict[str, Any]] = Field(default_factory=list)
    evidenceChain: Dict[str, Any] = Field(default_factory=dict)


class RagQueryRequest(BaseModel):
    # Full textbook chapters are accepted by the trusted question-generation
    # flow. The RAG routes keep the original 4,000-character limit for every
    # ordinary request.
    input: str = Field(min_length=1, max_length=210000)
    keyword: Optional[str] = Field(default=None, max_length=128)
    intent: str = Field(default="campus_search", max_length=64)
    ragStrategy: Optional[str] = Field(default=None, max_length=64)
    agentName: Optional[str] = Field(default=None, max_length=64)
    imageUrls: List[str] = Field(default_factory=list, max_length=8)
    images: List[str] = Field(default_factory=list, max_length=8)
    imageDataUrls: List[str] = Field(default_factory=list, max_length=8)
    attachments: List[Dict[str, Any]] = Field(default_factory=list, max_length=8)
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
    answerType: str = "text"
    documents: List[RagDocumentResponse] = Field(default_factory=list)
    trace: List[RagTraceResponse] = Field(default_factory=list)
    metadata: Dict[str, Any] = Field(default_factory=dict)
    outputType: str = "text"
    outputTypes: List[str] = Field(default_factory=list)
    outputMeta: Dict[str, Any] = Field(default_factory=dict)
    attachments: List[Dict[str, Any]] = Field(default_factory=list)
    resources: List[Dict[str, Any]] = Field(default_factory=list)
    evidenceChain: Dict[str, Any] = Field(default_factory=dict)
