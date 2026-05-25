# RAG 功能目录

本文档按 https://www.cnblogs.com/yupi/p/19914426 涉及的 RAG 方案整理骨架。

## Baseline
- `naive_rag`：基础检索增强生成。

## Query Transform
- `multi_query_rag`：多查询改写。
- `hyde`：假设文档扩展。

## Indexing
- `semantic_chunking`：语义切分。
- `parent_child`：父子块索引。

## Retrieval And Ranking
- `hybrid_search`：关键词 + 向量混合检索。
- `reranking`：候选文档重排。

## Corrective And Adaptive
- `crag`：纠错 RAG。
- `self_rag`：自反思 RAG。
- `adaptive_rag`：自适应路由。

## Structured Knowledge
- `graph_rag`：知识图谱 RAG。
- `text_to_sql`：自然语言转 SQL。

## Agentic
- `agentic_rag`：单智能体规划式 RAG。
- `multi_agent_rag`：多智能体协作 RAG。

## Multimodal And Performance
- `multimodal_rag`：多模态 RAG。
- `speculative_rag`：推测式 RAG。
