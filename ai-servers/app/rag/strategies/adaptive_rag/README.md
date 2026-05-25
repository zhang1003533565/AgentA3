# Adaptive RAG

## 这个功能是干什么的
根据问题复杂度和类型，动态选择普通检索、多查询、SQL、图谱或多智能体链路。

## 需要的框架组件
- AdaptiveRouter：策略路由。
- ComplexityClassifier：复杂度判断。
- StrategyRegistry：策略注册。

## 后续实现入口
- Runtime：`app/rag/strategies/adaptive_rag/`
- Router：`app/rag/routers/adaptive_router.py`
