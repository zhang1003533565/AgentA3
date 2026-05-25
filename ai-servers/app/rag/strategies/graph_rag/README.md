# GraphRAG

## 这个功能是干什么的
把知识组织成实体和关系图，通过图查询获得可解释的关系证据。

## 需要的框架组件
- EntityExtractor：抽取实体。
- GraphStore：存储实体关系。
- GraphRetriever：检索路径证据。

## 后续实现入口
- Runtime：`app/rag/strategies/graph_rag/`
- Agent：`app/multi_agents/graph_agent/`
