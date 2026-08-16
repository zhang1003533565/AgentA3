from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class ConversationState:
    session_id: str
    session_token: str
    authorization: str
    prompt: str
    input_text: str
    model: str
    user_id: Optional[int]
    rag_strategy: str = "direct_agent"
    rag_strategy_explicit: bool = False
    requested_agent: str = ""
    active_agent: str = "leader_agent"
    intent: str = "campus_search"
    history: List[Dict[str, str]] = field(default_factory=list)
    search_keyword: str = ""
    matched_results: List[Dict[str, Any]] = field(default_factory=list)
    retrieval_meta: Dict[str, Any] = field(default_factory=dict)
    trace: List[Dict[str, Any]] = field(default_factory=list)
    answer: str = ""
