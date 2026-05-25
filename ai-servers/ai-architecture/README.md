# AI Architecture Workspace

该目录用于沉淀 AI 服务架构，不直接影响当前 `app/` 运行逻辑。

## 目录说明

- `model_providers/`：模型服务商接入层，一家服务商一个目录
- `rag/`：RAG 策略、原理、检索组件与评测
- `multi_agents/`：多智能体编排，一 agent 一目录
- `skills/`：技能定义与模板
- `configs/`：Provider / RAG / Agent 配置
- `knowledge_bases/`：知识库原始数据、处理结果、向量库、图谱与 SQL schema
- `scripts/`：索引、评测、运维脚本
- `docs/`：架构文档、接口文档、运行手册
