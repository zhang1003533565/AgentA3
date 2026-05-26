class AdaptiveRagRouter:
    def route(self, query: str) -> str:
        normalized = (query or "").strip().lower()
        if not normalized:
            return "naive_rag"

        if any(token in normalized for token in ("图片", "照片", "表格", "pdf", "附件", "截图", "多模态")):
            return "multimodal_rag"
        if any(token in normalized for token in ("sql", "数据库", "统计", "多少个", "数量", "排名", "列表")):
            return "text_to_sql"
        if any(token in normalized for token in ("关系", "关联", "路径", "图谱", "实体")):
            return "graph_rag"
        if any(token in normalized for token in ("准确", "校验", "核查", "不确定", "有没有依据")):
            return "crag"
        if len(normalized) > 80 or any(token in normalized for token in ("为什么", "如何", "怎么", "比较", "区别")):
            return "multi_query_rag"
        if len(normalized) <= 12:
            return "naive_rag"
        return "hybrid_search"
