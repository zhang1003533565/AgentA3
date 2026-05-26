# RAG 功能目录

本文档按 https://www.cnblogs.com/yupi/p/19914426 涉及的 RAG 方案整理运行框架。

所有策略都统一注册到 `app.rag.engine.rag_engine`，并可通过 `ChatRequest.ragStrategy` 从聊天接口选择。

## Baseline
- `naive_rag`：基础检索增强生成，入口 `strategies/naive_rag/strategy.py`。

## Query Transform
- `multi_query_rag`：多查询改写，入口 `strategies/multi_query_rag/strategy.py`。
- `hyde`：假设文档扩展，入口 `strategies/hyde/strategy.py`。

## Indexing
- `semantic_chunking`：语义切分，入口 `strategies/semantic_chunking/strategy.py`。
- `parent_child`：父子块索引，入口 `strategies/parent_child/strategy.py`。

## Retrieval And Ranking
- `hybrid_search`：关键词 + 向量混合检索，入口 `strategies/hybrid_search/strategy.py`。
- `reranking`：候选文档重排，入口 `strategies/reranking/strategy.py`。

## Corrective And Adaptive
- `crag`：纠错 RAG，入口 `strategies/crag/strategy.py`。
- `self_rag`：自反思 RAG，入口 `strategies/self_rag/strategy.py`。
- `adaptive_rag`：自适应路由，入口 `strategies/adaptive_rag/strategy.py`。

## Structured Knowledge
- `graph_rag`：知识图谱 RAG，入口 `strategies/graph_rag/strategy.py`。
- `text_to_sql`：自然语言转 SQL，入口 `strategies/text_to_sql/strategy.py`。

## Agentic
- `agentic_rag`：单智能体规划式 RAG，入口 `strategies/agentic_rag/strategy.py`。
- `multi_agent_rag`：多智能体协作 RAG，入口 `strategies/multi_agent_rag/strategy.py`。

## Multimodal And Performance
- `multimodal_rag`：多模态 RAG，入口 `strategies/multimodal_rag/strategy.py`。
- `speculative_rag`：推测式 RAG，入口 `strategies/speculative_rag/strategy.py`。
