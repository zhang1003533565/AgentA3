import json
import re
from dataclasses import dataclass
from typing import Any, Dict, List

from fastapi import HTTPException

from app.multi_agents.runtime import complete_agent_or_raise


@dataclass(frozen=True)
class QuestionTypeAgent:
    name: str

    def generate_questions(self, topic: str, evidence: List[Dict[str, Any]], count: int = 5, chat_service=None) -> str:
        prompt = topic if count == 5 else f"{topic}\n\n题目数量要求：{count}"
        answer = complete_agent_or_raise(self.name, prompt, evidence, model_provider=chat_service)
        if self.name == "textbook_question_short_answer_agent":
            return _validate_short_answer_json_answer(self.name, answer)
        return _validate_json_answer(self.name, answer)


textbook_question_single_choice_agent = QuestionTypeAgent("textbook_question_single_choice_agent")
textbook_question_fill_blank_agent = QuestionTypeAgent("textbook_question_fill_blank_agent")
textbook_question_true_false_agent = QuestionTypeAgent("textbook_question_true_false_agent")
textbook_question_multiple_choice_agent = QuestionTypeAgent("textbook_question_multiple_choice_agent")


textbook_question_short_answer_agent = QuestionTypeAgent("textbook_question_short_answer_agent")


def _validate_short_answer_json_answer(agent_name: str, text: str) -> str:
    parsed = _parse_strict_json_answer(agent_name, text)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail=f"{agent_name} 返回的 JSON 顶层必须是对象")

    allowed_top_keys = {"questions", "missingInfo"}
    extra_keys = set(parsed.keys()) - allowed_top_keys
    if extra_keys:
        raise HTTPException(status_code=502, detail=f"{agent_name} 返回了不允许的顶层字段：{', '.join(sorted(extra_keys))}")
    if "questions" not in parsed or "missingInfo" not in parsed:
        raise HTTPException(status_code=502, detail=f"{agent_name} 必须返回 questions 和 missingInfo 字段")

    questions = parsed.get("questions")
    missing_info = parsed.get("missingInfo")
    if not isinstance(questions, list):
        raise HTTPException(status_code=502, detail=f"{agent_name}.questions 必须是数组")
    if not isinstance(missing_info, list):
        raise HTTPException(status_code=502, detail=f"{agent_name}.missingInfo 必须是数组")
    if not questions and not missing_info:
        raise HTTPException(status_code=502, detail=f"{agent_name} 未生成题目时必须在 missingInfo 中说明缺失信息")

    required_question_keys = {
        "id",
        "question",
        "knowledgePoints",
        "difficulty",
        "answerPoints",
        "scoringRubric",
        "totalScore",
        "sourceBasis",
    }
    allowed_difficulties = {"easy", "medium", "hard"}
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, dict):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}] 必须是对象")
        missing_keys = required_question_keys - set(question.keys())
        if missing_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}] 缺少字段：{', '.join(sorted(missing_keys))}")
        extra_question_keys = set(question.keys()) - required_question_keys
        if extra_question_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}] 返回了不允许的字段：{', '.join(sorted(extra_question_keys))}")
        for key in ("id", "question"):
            if not isinstance(question.get(key), str) or not question.get(key).strip():
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].{key} 必须是非空字符串")
        if question.get("difficulty") not in allowed_difficulties:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].difficulty 必须是 easy、medium 或 hard")
        for key in ("knowledgePoints", "answerPoints", "scoringRubric", "sourceBasis"):
            if not isinstance(question.get(key), list):
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].{key} 必须是数组")
            if not question.get(key):
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].{key} 不能为空")
        if not isinstance(question.get("totalScore"), (int, float)) or isinstance(question.get("totalScore"), bool):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].totalScore 必须是数字")
        _validate_answer_points(agent_name, index, question["answerPoints"])
        rubric_score = _validate_scoring_rubric(agent_name, index, question["scoringRubric"])
        if abs(float(question["totalScore"]) - rubric_score) > 0.0001:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].totalScore 必须等于 scoringRubric.score 之和")

    return json.dumps(parsed, ensure_ascii=False, indent=2)


def _validate_answer_points(agent_name: str, question_index: int, answer_points: List[Any]) -> None:
    for point_index, answer_point in enumerate(answer_points, start=1):
        if not isinstance(answer_point, dict):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].answerPoints[{point_index}] 必须是对象")
        allowed_keys = {"point", "sourceBasis"}
        missing_keys = allowed_keys - set(answer_point.keys())
        if missing_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].answerPoints[{point_index}] 缺少字段：{', '.join(sorted(missing_keys))}")
        extra_keys = set(answer_point.keys()) - allowed_keys
        if extra_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].answerPoints[{point_index}] 返回了不允许的字段：{', '.join(sorted(extra_keys))}")
        if not isinstance(answer_point.get("point"), str) or not answer_point.get("point").strip():
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].answerPoints[{point_index}].point 必须是非空字符串")
        if not isinstance(answer_point.get("sourceBasis"), list) or not answer_point.get("sourceBasis"):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].answerPoints[{point_index}].sourceBasis 必须是非空数组")


def _validate_scoring_rubric(agent_name: str, question_index: int, scoring_rubric: List[Any]) -> float:
    score_sum = 0.0
    for rubric_index, rubric in enumerate(scoring_rubric, start=1):
        if not isinstance(rubric, dict):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].scoringRubric[{rubric_index}] 必须是对象")
        allowed_keys = {"criterion", "score"}
        missing_keys = allowed_keys - set(rubric.keys())
        if missing_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].scoringRubric[{rubric_index}] 缺少字段：{', '.join(sorted(missing_keys))}")
        extra_keys = set(rubric.keys()) - allowed_keys
        if extra_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].scoringRubric[{rubric_index}] 返回了不允许的字段：{', '.join(sorted(extra_keys))}")
        if not isinstance(rubric.get("criterion"), str) or not rubric.get("criterion").strip():
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].scoringRubric[{rubric_index}].criterion 必须是非空字符串")
        score = rubric.get("score")
        if not isinstance(score, (int, float)) or isinstance(score, bool) or score <= 0:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].scoringRubric[{rubric_index}].score 必须是正数")
        score_sum += float(score)
    return score_sum


def _parse_strict_json_answer(agent_name: str, text: str) -> Any:
    answer = (text or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空")
    if answer.startswith("```") or answer.endswith("```"):
        raise HTTPException(status_code=502, detail=f"{agent_name} 必须直接返回 JSON，不能包含 Markdown 代码块")
    try:
        return json.loads(answer)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"{agent_name} 返回的内容不是合法的严格 JSON：{str(exc)}。请直接返回 JSON 对象，不要包含 Markdown 标记或其他额外文本。",
        )


def _validate_json_answer(agent_name: str, text: str) -> str:
    answer = (text or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空")

    json_match = re.search(r"```(?:json)?\s*([\s\S]*?)```", answer, flags=re.IGNORECASE)
    if json_match:
        answer = json_match.group(1).strip()

    try:
        parsed = json.loads(answer)
        return json.dumps(parsed, ensure_ascii=False, indent=2)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"{agent_name} 返回的内容不是合法的 JSON 格式：{str(exc)}。请确保返回严格的 JSON 格式，不要包含 Markdown 标记或其他额外文本。",
        )



__all__ = ["textbook_question_short_answer_agent"]
