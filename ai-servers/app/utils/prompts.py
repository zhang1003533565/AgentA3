import json
from typing import Any, Dict, List

DEFAULT_SYSTEM_PROMPT = "你是智慧校园助手，请基于已有上下文和用户输入进行清晰、简洁的回答。"

KEYWORD_EXTRACTION_PROMPT = (
    "你是一个搜索词提取器。"
    "请从用户的问题中提取最适合做校园食堂/档口/菜品搜索的核心关键词。"
    "只返回关键词本身，不要解释，不要标点，不要句子。"
    "如果问题中已经有明确菜名、品类或店铺特征，就优先返回那个词。"
    "例如：'那家的麻辣烫好吃' 返回 '麻辣烫'；"
    "'哪个食堂有黄焖鸡' 返回 '黄焖鸡'。"
)


def build_search_facts_prompt(keyword: str, search_results: List[Dict[str, Any]]) -> str:
    if keyword == "课表查询":
        if not search_results:
            return "系统已执行课表查询，但当前没有查到匹配课程。请明确告诉用户：可能本周无课、当天无课，或尚未导入课表。"
        return (
            "系统已经完成当前用户的课表查询。下面这些是受控查询返回的课程结果，"
            "请严格依据这些课程信息回答，不要编造不存在的课程安排："
            + json.dumps(search_results, ensure_ascii=False)
        )

    if not search_results:
        return f"系统搜索关键词为：{keyword}。当前没有找到完全匹配的食堂/档口/菜品，请明确告诉用户未匹配到，并给出更适合的关键词建议。"

    return (
        f"系统已经根据关键词 `{keyword}` 做了本地词语匹配。"
        "下面这些是受控搜索函数返回的精确候选，请优先依据这些结果回答，不要编造不存在的数据："
        + json.dumps(search_results, ensure_ascii=False)
    )
