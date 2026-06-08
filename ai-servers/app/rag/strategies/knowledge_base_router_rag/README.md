# 知识库路由 RAG

## 作用

根据 `knowledgeBaseIds` 将问题路由到指定知识库集合内检索，避免多知识库混检时出现无关来源。

## 适用场景

- 管理台召回测试中选择一个或多个知识库。
- 用户问题跨多个业务域，需要只在目标域内检索。
- 教学资料、后勤服务、活动通知等知识库并存。

## 输入参数

从 `RagQuery.metadata` 读取：

- `knowledgeBaseIds`
- `knowledgeBaseId`
