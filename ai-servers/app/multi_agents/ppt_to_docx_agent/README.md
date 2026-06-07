# ppt_to_docx_agent

PPT 转 DOCX 智能体用于把 `.ppt` 或 `.pptx` 文件转换为 `.docx`。它是确定性文件处理智能体，不让 LLM 猜测 PPT 内容。

转换入口：
- Python：`POST /internal/rag/ppt/convert`
- Java：`POST /api/ai/rag/ppt/convert`
- 前端：`/ai/rag` 的“文档转换”页签

转换原则：
- 按幻灯片顺序生成 Word 章节。
- 文本、表格、图片按页面阅读顺序写入。
- PPT 的精确坐标排版不强制保留，Word 内容会重新排版。
- `.pptx` 会直接解析；`.ppt` 会先通过 LibreOffice/soffice 转为临时 `.pptx`，再生成 DOCX。
