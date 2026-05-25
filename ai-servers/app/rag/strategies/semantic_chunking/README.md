# Semantic Chunking

## 这个功能是干什么的
按语义边界切分文档，而不是固定长度截断，减少上下文断裂。

## 需要的框架组件
- Parser：提取文档正文。
- SemanticChunker：按语义切分。
- EmbeddingStore：写入向量库。

## 后续实现入口
- Runtime：`app/rag/strategies/semantic_chunking/`
- Chunker：`app/rag/chunking/semantic.py`
