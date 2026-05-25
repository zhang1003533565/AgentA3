# Parent-Child

## 这个功能是干什么的
用小块做精确召回，用父块补充更完整上下文，兼顾精度和上下文完整性。

## 需要的框架组件
- ParentChildChunker：生成父块和子块。
- ChildRetriever：检索子块。
- ParentResolver：回填父块上下文。

## 后续实现入口
- Runtime：`app/rag/strategies/parent_child/`
- Chunker：`app/rag/chunking/parent_child.py`
