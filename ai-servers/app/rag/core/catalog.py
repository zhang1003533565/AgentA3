RAG_STRATEGY_SPECS = {
    "naive_rag": {
        "category": "baseline",
        "purpose": "基础 RAG：检索相关文档后直接生成回答。",
    },
    "multi_query_rag": {
        "category": "query_transform",
        "purpose": "多查询改写：从多个角度扩展用户问题后合并召回。",
    },
    "hyde": {
        "category": "query_transform",
        "purpose": "假设文档扩展：先生成假想答案/文档，再用它检索。",
    },
    "semantic_chunking": {
        "category": "indexing",
        "purpose": "语义切分：按语义边界分块，减少上下文断裂。",
    },
    "parent_child": {
        "category": "indexing",
        "purpose": "父子块检索：小块召回，大块补充上下文。",
    },
    "hybrid_search": {
        "category": "retrieval",
        "purpose": "混合检索：结合关键词检索和向量检索。",
    },
    "reranking": {
        "category": "ranking",
        "purpose": "重排：对候选文档二次排序，提高最终上下文质量。",
    },
    "crag": {
        "category": "corrective",
        "purpose": "纠错 RAG：评估召回质量，必要时改写或补检索。",
    },
    "self_rag": {
        "category": "corrective",
        "purpose": "自反思 RAG：由模型判断是否需要检索、是否证据充分。",
    },
    "adaptive_rag": {
        "category": "routing",
        "purpose": "自适应 RAG：根据问题复杂度动态选择策略。",
    },
    "graph_rag": {
        "category": "structured_knowledge",
        "purpose": "图谱 RAG：使用实体关系图谱辅助检索与推理。",
    },
    "text_to_sql": {
        "category": "structured_knowledge",
        "purpose": "Text-to-SQL：将自然语言转成只读 SQL 查询结构化数据。",
    },
    "agentic_rag": {
        "category": "agentic",
        "purpose": "Agentic RAG：由智能体规划检索、工具调用和生成。",
    },
    "multi_agent_rag": {
        "category": "agentic",
        "purpose": "多智能体 RAG：多个角色协作完成复杂问答。",
    },
    "multimodal_rag": {
        "category": "multimodal",
        "purpose": "多模态 RAG：支持图片、表格、PDF 等多模态资料。",
    },
    "speculative_rag": {
        "category": "performance",
        "purpose": "推测式 RAG：先生成草稿，再并行验证和修订。",
    },
}
