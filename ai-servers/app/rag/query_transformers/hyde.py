class HydeTransformer:
    def transform(self, query: str) -> str:
        normalized = (query or "").strip()
        if not normalized:
            return ""
        return (
            f"用户问题：{normalized}\n"
            "可能相关的校园知识文档通常会包含办理地点、开放时间、申请流程、所需材料、"
            "负责部门、注意事项、联系方式和服务说明。请检索与该问题语义最接近的知识片段。"
        )
