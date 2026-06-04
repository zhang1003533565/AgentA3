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
        if self.name == "textbook_question_programming_agent":
            return _validate_programming_json_answer(self.name, answer)
        return _validate_json_answer(self.name, answer)


textbook_question_single_choice_agent = QuestionTypeAgent("textbook_question_single_choice_agent")
textbook_question_fill_blank_agent = QuestionTypeAgent("textbook_question_fill_blank_agent")
textbook_question_true_false_agent = QuestionTypeAgent("textbook_question_true_false_agent")
textbook_question_multiple_choice_agent = QuestionTypeAgent("textbook_question_multiple_choice_agent")
textbook_question_short_answer_agent = QuestionTypeAgent("textbook_question_short_answer_agent")
textbook_question_calculation_agent = QuestionTypeAgent("textbook_question_calculation_agent")


textbook_question_programming_agent = QuestionTypeAgent("textbook_question_programming_agent")

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


def _validate_programming_json_answer(agent_name: str, text: str) -> str:
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
        "title",
        "knowledgePoints",
        "difficulty",
        "language",
        "description",
        "inputFormat",
        "outputFormat",
        "constraints",
        "examples",
        "testCases",
        "solutionOutline",
        "referenceSolution",
        "sourceBasis",
    }
    allowed_difficulties = {"easy", "medium", "hard"}
    for index, question in enumerate(questions, start=1):
        if not isinstance(question, dict):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}] 必须是对象")
        missing_keys = required_question_keys - set(question.keys())
        if missing_keys:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}] 缺少字段：{', '.join(sorted(missing_keys))}")
        if question.get("difficulty") not in allowed_difficulties:
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].difficulty 必须是 easy、medium 或 hard")
        for key in ("knowledgePoints", "constraints", "examples", "testCases", "solutionOutline", "sourceBasis"):
            if not isinstance(question.get(key), list):
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].{key} 必须是数组")
        for key in ("id", "title", "language", "description", "inputFormat", "outputFormat", "referenceSolution"):
            if not isinstance(question.get(key), str):
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].{key} 必须是字符串")
        if question.get("language") == "未指定" and question.get("referenceSolution", "").strip():
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}] 未指定语言时 referenceSolution 必须为空字符串")
        if not question.get("knowledgePoints"):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].knowledgePoints 不能为空")
        if not question.get("sourceBasis"):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{index}].sourceBasis 不能为空，必须说明生成依据")
        _validate_examples(agent_name, index, question["examples"])
        _validate_test_cases(agent_name, index, question["testCases"])

    return json.dumps(parsed, ensure_ascii=False, indent=2)


def _validate_examples(agent_name: str, question_index: int, examples: List[Any]) -> None:
    for example_index, example in enumerate(examples, start=1):
        if not isinstance(example, dict):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].examples[{example_index}] 必须是对象")
        for key in ("input", "output", "explanation"):
            if not isinstance(example.get(key), str):
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].examples[{example_index}].{key} 必须是字符串")


def _validate_test_cases(agent_name: str, question_index: int, test_cases: List[Any]) -> None:
    for case_index, test_case in enumerate(test_cases, start=1):
        if not isinstance(test_case, dict):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].testCases[{case_index}] 必须是对象")
        for key in ("input", "expectedOutput"):
            if not isinstance(test_case.get(key), str):
                raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].testCases[{case_index}].{key} 必须是字符串")
        if not isinstance(test_case.get("hidden"), bool):
            raise HTTPException(status_code=502, detail=f"{agent_name}.questions[{question_index}].testCases[{case_index}].hidden 必须是布尔值")


def _parse_json_answer(agent_name: str, text: str) -> Any:
    answer = (text or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空")

    json_match = re.search(r"```(?:json)?\s*([\s\S]*?)```", answer, flags=re.IGNORECASE)
    if json_match:
        answer = json_match.group(1).strip()

    try:
        return json.loads(answer)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"{agent_name} 返回的内容不是合法的 JSON 格式：{str(exc)}。请确保返回严格的 JSON 格式，不要包含 Markdown 标记或其他额外文本。",
        )


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


__all__ = ["textbook_question_programming_agent"]
