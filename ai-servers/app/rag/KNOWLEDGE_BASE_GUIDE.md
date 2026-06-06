# Knowledge Base Build Guide

该项目的知识库搭建遵循“离线索引 + 在线检索”的 RAG 流程。

## 1. 离线索引

1. 准备资料：把 Markdown、TXT、CSV、TSV、JSON、HTML、PDF、图片放入 `ai-servers/knowledge_base/raw`。
2. 文档解析：`DocumentLoader` 和 `MultimodalParser` 读取文本、表格、PDF 和图片元数据。
3. 文本切分：普通索引用 `SemanticChunker`，Parent-Child 索引用 `ParentChildChunker`。
4. 向量写入：默认 `RAG_VECTOR_STORE_BACKEND=milvus`，写入 Docker Milvus。
5. 索引产物：
   - 普通 chunk：`RAG_MILVUS_COLLECTION`
   - Parent-Child chunk：`RAG_MILVUS_PARENT_CHILD_COLLECTION`

## 2. Docker Milvus

在 `ai-servers` 目录启动：

```bash
docker compose up -d
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

`local_jsonl` 只保留显式指定时的兼容能力，不再作为知识库默认方案。

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

`http://localhost:5174/ai/knowledge-base`

- 上传或编辑文档并写入 Milvus。
- 查看向量库、Embedding、图谱库状态。
- 已入库文档列表来自 Milvus collection 聚合，不扫描本地 raw 目录。
