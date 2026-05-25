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
    searchKeyword: str
    matchedResults: List[Dict[str, Any]]
    answer: str
