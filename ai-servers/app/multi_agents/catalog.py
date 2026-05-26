from pathlib import Path
from typing import Any, Dict, List, Optional


AGENT_ORDER = [
    "orchestrator_agent",
    "planner_agent",
    "retriever_agent",
    "answer_agent",
    "critic_agent",
    "memory_agent",
    "sql_agent",
    "graph_agent",
    "tool_agent",
]

AGENT_PROFILES: Dict[str, Dict[str, Any]] = {
    "orchestrator_agent": {
        "role": "流程编排智能体",
        "purpose": "根据 RAG 策略调度 Planner、Retriever、Answer、Critic 等子智能体，统一记录 trace 和回退。",
        "inputs": ["user_query", "rag_strategy", "global_state"],
        "outputs": ["execution_trace", "final_result"],
        "skills": ["agent orchestration", "fallback routing", "trace management"],
    },
    "planner_agent": {
        "role": "意图规划智能体",
        "purpose": "识别闲聊、课表、SQL、图谱、多模态等意图，并决定是否需要检索。",
        "inputs": ["user_query", "context"],
        "outputs": ["intent", "need_retrieval", "plan_steps"],
        "skills": ["intent routing", "task planning", "strategy selection"],
    },
    "retriever_agent": {
        "role": "证据召回智能体",
        "purpose": "按策略执行 Java 后端检索、向量检索、混合检索、父子块、CRAG、Self-RAG 等证据召回。",
        "inputs": ["authorization", "intent", "keyword", "input_text", "rag_strategy"],
        "outputs": ["matched_results", "retrieval_meta"],
        "skills": ["vector retrieval", "hybrid search", "reranking", "corrective retrieval"],
    },
    "answer_agent": {
        "role": "答案生成智能体",
        "purpose": "基于提示词、历史、关键词和证据生成最终自然语言回答。",
        "inputs": ["prompt", "input_text", "history", "search_keyword", "search_results"],
        "outputs": ["answer"],
        "skills": ["grounded answering", "context synthesis", "response formatting"],
    },
    "critic_agent": {
        "role": "答案审核智能体",
        "purpose": "对生成答案做轻量修订，减少冗余和明显不一致。",
        "inputs": ["draft_answer", "evidence"],
        "outputs": ["refined_answer", "issues"],
        "skills": ["quality check", "faithfulness review", "copy refinement"],
    },
    "memory_agent": {
        "role": "会话记忆智能体",
        "purpose": "读取和写入多轮对话记忆，保证会话上下文连续。",
        "inputs": ["session_token", "write_turn"],
        "outputs": ["history"],
        "skills": ["short-term memory", "history windowing", "session persistence"],
    },
    "sql_agent": {
        "role": "Text-to-SQL 智能体",
        "purpose": "负责 Text-to-SQL，把自然语言问题转换为安全只读 SQL，并返回结构化结果。",
        "inputs": ["user_query", "schema"],
        "outputs": ["sql", "rows"],
        "skills": ["readonly SQL generation", "schema routing", "safe query validation"],
    },
    "graph_agent": {
        "role": "GraphRAG 智能体",
        "purpose": "从本地图谱或 Neo4j 中检索实体关系路径，为 GraphRAG 提供可解释证据。",
        "inputs": ["user_query"],
        "outputs": ["entities", "relations", "evidence_paths"],
        "skills": ["entity extraction", "graph path retrieval", "structured evidence"],
    },
    "tool_agent": {
        "role": "工具选择智能体",
        "purpose": "根据任务选择 Text-to-SQL、图谱、混合检索等工具，并标准化调用参数。",
        "inputs": ["task", "available_tools"],
        "outputs": ["tool_name", "tool_args", "tool_result"],
        "skills": ["tool routing", "argument shaping", "tool fallback"],
    },
}


def get_agent_catalog() -> Dict[str, Any]:
    agents = [_build_agent(agent_name, include_documents=True) for agent_name in AGENT_ORDER]
    return {
        "total": len(agents),
        "workflow": {
            "default": ["planner_agent", "retriever_agent", "answer_agent", "critic_agent", "memory_agent"],
            "textToSql": ["planner_agent", "sql_agent", "answer_agent", "critic_agent", "memory_agent"],
            "graphRag": ["planner_agent", "graph_agent", "retriever_agent", "answer_agent", "critic_agent", "memory_agent"],
            "agentic": ["orchestrator_agent", "planner_agent", "tool_agent", "retriever_agent", "answer_agent", "critic_agent", "memory_agent"],
        },
        "agents": agents,
    }


def get_agent_detail(agent_name: str) -> Optional[Dict[str, Any]]:
    if agent_name not in AGENT_PROFILES:
        return None
    return _build_agent(agent_name, include_documents=True)


def _build_agent(agent_name: str, include_documents: bool) -> Dict[str, Any]:
    agent_dir = _agent_dir(agent_name)
    profile = dict(AGENT_PROFILES[agent_name])
    payload: Dict[str, Any] = {
        "name": agent_name,
        "role": profile["role"],
        "purpose": profile["purpose"],
        "inputs": profile["inputs"],
        "outputs": profile["outputs"],
        "skills": profile["skills"],
        "runtime": f"app.multi_agents.{agent_name}.agent",
        "directory": str(agent_dir),
        "files": {
            "agent": str(agent_dir / "agent.py"),
            "skill": str(agent_dir / "skill.md"),
            "prompt": str(agent_dir / "prompt.md"),
            "contract": str(agent_dir / "contract.md"),
            "tools": str(agent_dir / "tools.yaml"),
        },
    }
    if include_documents:
        payload["documents"] = {
            "skill": _read_text(agent_dir / "skill.md"),
            "prompt": _read_text(agent_dir / "prompt.md"),
            "contract": _read_text(agent_dir / "contract.md"),
            "tools": _read_text(agent_dir / "tools.yaml"),
            "readme": _read_text(agent_dir / "README.md"),
        }
    return payload


def _agent_dir(agent_name: str) -> Path:
    return Path(__file__).resolve().parent / agent_name


def _read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")
