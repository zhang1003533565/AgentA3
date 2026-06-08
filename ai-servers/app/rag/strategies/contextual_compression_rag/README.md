# 上下文压缩 RAG

## 作用

召回文档后，只保留与问题词高度相关的句子，减少长 chunk 中无关内容对生成回答的干扰。

## 适用场景

- chunk 较长、包含多个主题。
- LLM 上下文窗口有限。
- 希望降低无关段落进入答案生成的概率。

## 输出特征

返回文档 metadata 会包含：

- `compressed`
- `originalLength`
- `compressedLength`
