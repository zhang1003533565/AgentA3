# Knowledge Base Build Guide

该项目的知识库搭建遵循“离线索引 + 在线检索”的 RAG 流程。

## 1. 离线索引

1. 准备资料：把 Markdown、TXT、CSV、TSV、JSON、HTML、PDF、图片放入 `ai-servers/knowledge_base/raw`。
2. 文档解析：`DocumentLoader` 和 `MultimodalParser` 读取文本、表格、PDF 和图片元数据。
3. 文本切分：普通索引用 `SemanticChunker`，Parent-Child 索引用 `ParentChildChunker`。
4. 向量写入：`RAG_VECTOR_STORE_BACKEND=local_jsonl` 写本地 JSONL；`milvus` 写 Docker Milvus。
5. 索引产物：
   - 普通 chunk：`.index/local_chunks.jsonl`
   - Parent-Child chunk：`.index/parent_child_chunks.jsonl`
   - Milvus collection：`RAG_MILVUS_COLLECTION`

## 2. Docker Milvus

从项目根目录启动：

```bash
docker compose -f docker-compose.rag.yml up -d
```

`ai-servers/.env` 推荐配置：

```env
RAG_VECTOR_STORE_BACKEND=milvus
RAG_MILVUS_URI=http://localhost:19530
RAG_MILVUS_COLLECTION=smart_campus_knowledge
RAG_MILVUS_DIMENSION=384
RAG_MILVUS_METRIC_TYPE=COSINE
```

安装 Python 依赖：

```bash
cd ai-servers
pip install -r requirements.txt
```

## 3. 构建知识库

```bash
cd ai-servers
python3 scripts/build_knowledge_base.py --backend milvus
```

没有 Docker 时可先使用本地索引：

```bash
python3 scripts/build_knowledge_base.py --backend local_jsonl
```

## 4. 在线检索策略

- `naive_rag`：向量 TopK 检索，适合基础问答。
- `hybrid_search`：关键词 + 向量融合，适合中文关键词明确的校园资料。
- `parent_child`：子块命中后返回父块上下文，适合长文档、制度说明、教材章节。
- `reranking`：Hybrid 后再按词项覆盖重排。
- `multi_query_rag`：把一个问题扩展成多种问法，提升召回。
- `hyde`：先生成假设答案/文档，再用它检索。
- `graph_rag`：图谱路径优先，Hybrid 兜底。

建议默认顺序：

1. 新知识库先用 `hybrid_search` 验证召回。
2. 长文档/教材问答用 `parent_child`。
3. 问法不稳定时用 `multi_query_rag` 或 `hyde`。
4. 对准确率要求更高时用 `reranking` 或 `crag`。

## 5. 管理端入口

`http://localhost:5174/ai/rag`

- “知识库”页签：上传或编辑文档并入库。
- “策略与健康”页签：查看向量库、Embedding、图谱库状态。
- “智能体执行/多智能体”页签：选择策略和智能体验证检索效果。
