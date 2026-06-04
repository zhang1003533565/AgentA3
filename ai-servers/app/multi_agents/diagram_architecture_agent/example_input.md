智慧校园 AI RAG 架构材料

前端 AppWeb 的 RAG 管理页面负责展示智能体列表、输入材料、模型选择和执行结果。

Java 后端 AppBackend 负责鉴权、读取 AI 模型配置、代理调用 Python AI 服务，并把请求转发给 RAG 接口。

Python ai-servers 负责 RAG 检索、智能体路由、专业智能体执行和图片/图表生成逻辑。

知识库包含文档、向量库、图数据库和文本检索能力。模型服务由 Java 侧配置后转发给 Python 使用。
