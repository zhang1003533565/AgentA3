# leader_agent

Leader 智能体是统一入口：识别用户目标、直接回答或调用已启用的系统工具并保存对话记忆；专业智能体封装在工具内部，不作为 Leader 的独立路由目标。本目录同时维护运行代码、skill、prompt、contract 和工具声明。第三方知识库由 Java 后端对接，AI Server 不再维护本地检索策略。
