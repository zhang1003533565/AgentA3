from pathlib import Path
from typing import Any, Dict, Optional


AGENT_ORDER = [
    "leader_agent",
    "mind_map_agent",
    "md_knowledge_agent",
    "textbook_knowledge_agent",
    "textbook_question_bank_agent",
    "ppt_agent",
    "image_agent",
]

AGENT_PROFILES: Dict[str, Dict[str, Any]] = {
    "leader_agent": {
        "role": "Leader 智能体",
        "purpose": "统一理解用户任务，路由到思维导图、MD 知识点、教材知识点、教材题库、PPT、图片等专业智能体，并管理会话记忆与最终回答兜底。",
        "inputs": ["user_query", "rag_strategy", "session_token", "history"],
        "outputs": ["intent", "target_agent", "need_retrieval", "answer"],
        "skills": ["task routing", "agent orchestration", "memory", "fallback answering"],
        "intent": "auto",
        "needRetrieval": False,
        "defaultRagStrategy": "naive_rag",
        "aliases": ["leader", "leader_agent", "总控智能体", "leader智能体"],
    },
    "mind_map_agent": {
        "role": "思维导图智能体",
        "purpose": "把教材知识点或用户主题整理成层级化 Markdown/Mermaid 思维导图。",
        "inputs": ["topic", "evidence"],
        "outputs": ["mind_map_markdown"],
        "skills": ["mind map", "hierarchy extraction", "mermaid"],
        "intent": "mind_map",
        "needRetrieval": True,
        "defaultRagStrategy": "multi_agent_rag",
        "aliases": ["mind_map", "mindmap", "思维导图", "思维导图智能体", "脑图智能体"],
    },
    "md_knowledge_agent": {
        "role": "MD 知识点提取智能体",
        "purpose": "从 Markdown、课堂笔记或普通文本中提取标题、列表、编号项和核心知识点。",
        "inputs": ["markdown_text", "evidence"],
        "outputs": ["knowledge_points"],
        "skills": ["markdown parsing", "keyword extraction", "knowledge point extraction"],
        "intent": "md_knowledge",
        "needRetrieval": False,
        "defaultRagStrategy": "semantic_chunking",
        "aliases": ["md_knowledge", "markdown_knowledge", "md知识点提取", "md知识点提取智能体"],
    },
    "textbook_knowledge_agent": {
        "role": "教材知识点智能体",
        "purpose": "围绕教材章节、课程内容、知识点和考点做检索增强，复用 Java 后端和本地 RAG 能力召回证据。",
        "inputs": ["authorization", "intent", "keyword", "input_text", "rag_strategy"],
        "outputs": ["matched_results", "retrieval_meta"],
        "skills": ["textbook retrieval", "hybrid search", "reranking", "graph/text-to-sql retrieval"],
        "intent": "textbook_knowledge",
        "needRetrieval": True,
        "defaultRagStrategy": "hybrid_search",
        "aliases": ["textbook_knowledge", "教材知识点", "教材知识点智能体", "课本知识点智能体"],
    },
    "textbook_question_bank_agent": {
        "role": "教材题库智能体",
        "purpose": "基于教材知识点生成练习题、简答题、参考答案和复习检测题。",
        "inputs": ["topic", "evidence", "count"],
        "outputs": ["question_bank_markdown"],
        "skills": ["question generation", "answer key", "assessment design"],
        "intent": "question_bank",
        "needRetrieval": True,
        "defaultRagStrategy": "multi_agent_rag",
        "aliases": ["question_bank", "textbook_question_bank", "教材题库", "教材题库智能体", "题库智能体"],
    },
    "ppt_agent": {
        "role": "PPT 智能体",
        "purpose": "把主题和知识点证据整理成 PPT 页结构、讲解目标和页面内容建议。",
        "inputs": ["topic", "evidence", "slide_count"],
        "outputs": ["ppt_outline_markdown"],
        "skills": ["slide outline", "teaching flow", "presentation planning"],
        "intent": "ppt",
        "needRetrieval": True,
        "defaultRagStrategy": "multi_agent_rag",
        "aliases": ["ppt", "ppt_agent", "课件智能体", "PPT智能体"],
    },
    "image_agent": {
        "role": "图片智能体",
        "purpose": "根据课程主题和知识点证据生成教学配图、封面图、插图提示词。",
        "inputs": ["topic", "evidence"],
        "outputs": ["image_prompt"],
        "skills": ["image prompt", "visual planning", "multimodal context"],
        "intent": "image",
        "needRetrieval": True,
        "defaultRagStrategy": "multimodal_rag",
        "aliases": ["image", "image_agent", "图片智能体", "配图智能体"],
    },
}

AGENT_ALIASES = {
    alias.lower(): agent_name
    for agent_name, profile in AGENT_PROFILES.items()
    for alias in [agent_name, *profile.get("aliases", [])]
}


def get_agent_catalog() -> Dict[str, Any]:
    agents = [_build_agent(agent_name, include_documents=True) for agent_name in AGENT_ORDER]
    return {
        "total": len(agents),
        "workflow": {
            "default": ["leader_agent", "textbook_knowledge_agent", "md_knowledge_agent"],
            "mindMap": ["leader_agent", "textbook_knowledge_agent", "mind_map_agent"],
            "markdownKnowledge": ["leader_agent", "md_knowledge_agent"],
            "textbookKnowledge": ["leader_agent", "textbook_knowledge_agent", "md_knowledge_agent"],
            "questionBank": ["leader_agent", "textbook_knowledge_agent", "textbook_question_bank_agent"],
            "ppt": ["leader_agent", "textbook_knowledge_agent", "ppt_agent"],
            "image": ["leader_agent", "textbook_knowledge_agent", "image_agent"],
        },
        "agents": agents,
    }


def get_agent_detail(agent_name: str) -> Optional[Dict[str, Any]]:
    agent_name = normalize_agent_name(agent_name) or ""
    if agent_name not in AGENT_PROFILES:
        return None
    return _build_agent(agent_name, include_documents=True)


def normalize_agent_name(agent_name: Optional[str]) -> Optional[str]:
    value = (agent_name or "").strip()
    if not value:
        return None
    return AGENT_ALIASES.get(value.lower())


def get_agent_profile(agent_name: Optional[str]) -> Optional[Dict[str, Any]]:
    normalized = normalize_agent_name(agent_name)
    if not normalized:
        return None
    return {"name": normalized, **AGENT_PROFILES[normalized]}


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
        "intent": profile["intent"],
        "needRetrieval": profile["needRetrieval"],
        "defaultRagStrategy": profile["defaultRagStrategy"],
        "aliases": profile["aliases"],
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
