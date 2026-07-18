import json
from typing import Any, Dict, List, Optional, Set

from fastapi import HTTPException


QUESTION_AGENT_TYPES: Dict[str, str] = {
    "textbook_question_single_choice_agent": "single_choice",
    "textbook_question_multiple_choice_agent": "multiple_choice",
    "textbook_question_true_false_agent": "true_false",
    "textbook_question_fill_blank_agent": "fill_blank",
    "textbook_question_short_answer_agent": "short_answer",
    "textbook_question_calculation_agent": "calculation",
    "textbook_question_programming_agent": "programming",
}

ALLOWED_QUESTION_TYPES: Set[str] = {
    "single_choice",
    "multiple_choice",
    "true_false",
    "fill_blank",
    "short_answer",
    "essay",
    "material_analysis",
    "calculation",
    "proof",
    "programming",
    "operation",
    "matching",
    "ordering",
    "cloze",
}

ALLOWED_DIFFICULTIES = {"easy", "medium", "hard"}
ALLOWED_SCORING_MODES = {"exact", "blank", "rubric", "step", "program", "manual"}
TOP_LEVEL_KEYS = {"questions", "missingInfo"}
QUESTION_REQUIRED_KEYS = {
    "id",
    "type",
    "stem",
    "score",
    "difficulty",
    "knowledgePoints",
    "tags",
    "body",
    "answer",
    "analysis",
    "scoring",
    "sourceBasis",
}


def expected_type_for_agent(agent_name: str) -> Optional[str]:
    return QUESTION_AGENT_TYPES.get(agent_name or "")


def parse_and_validate_question_bank_answer(agent_name: str, text: str) -> str:
    payload = parse_strict_json_text(agent_name, text)
    expected_type = expected_type_for_agent(agent_name)
    review = review_question_bank_payload(payload, expected_type=expected_type)
    if not review["valid"]:
        issues = "；".join(review["issues"][:12])
        raise HTTPException(
            status_code=502,
            detail=f"{agent_name} 返回的题库 JSON 未通过校验：{issues}",
        )
    return json.dumps(payload, ensure_ascii=False, indent=2)


def parse_strict_json_text(agent_name: str, text: str) -> Dict[str, Any]:
    answer = (text or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail=f"{agent_name} LLM 返回内容为空")
    if answer.startswith("```") or answer.endswith("```"):
        raise HTTPException(status_code=502, detail=f"{agent_name} 必须直接返回 JSON，不能包含 Markdown 代码块")
    try:
        parsed = json.loads(answer)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"{agent_name} 返回的内容不是合法的严格 JSON：{str(exc)}。请直接返回 JSON 对象，不要包含 Markdown 标记或其他额外文本。",
        ) from exc
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail=f"{agent_name} 返回的 JSON 顶层必须是对象")
    return parsed


def review_question_bank_payload(
    payload: Any,
    expected_type: Optional[str] = None,
) -> Dict[str, Any]:
    issues: List[str] = []
    warnings: List[str] = []
    types: List[str] = []

    if not isinstance(payload, dict):
        return {
            "valid": False,
            "issues": ["payload 必须是 JSON 对象"],
            "warnings": [],
            "questionCount": 0,
            "types": [],
        }

    extra_keys = set(payload.keys()) - TOP_LEVEL_KEYS
    missing_top_keys = TOP_LEVEL_KEYS - set(payload.keys())
    if extra_keys:
        issues.append(f"顶层不允许出现额外字段：{', '.join(sorted(extra_keys))}")
    if missing_top_keys:
        issues.append(f"顶层缺少字段：{', '.join(sorted(missing_top_keys))}")

    questions = payload.get("questions")
    missing_info = payload.get("missingInfo")
    if not isinstance(questions, list):
        issues.append("questions 必须是数组")
        questions = []
    if not isinstance(missing_info, list):
        issues.append("missingInfo 必须是数组")
    if not questions and not missing_info:
        issues.append("未生成题目时 missingInfo 必须说明缺失信息")

    for index, question in enumerate(questions, start=1):
        question_path = f"questions[{index}]"
        _review_question(question, question_path, issues, warnings, expected_type=expected_type)
        if isinstance(question, dict) and isinstance(question.get("type"), str):
            types.append(question["type"])

    return {
        "valid": len(issues) == 0,
        "issues": issues,
        "warnings": warnings,
        "questionCount": len(questions),
        "types": sorted(set(types)),
    }


def _review_question(
    question: Any,
    path: str,
    issues: List[str],
    warnings: List[str],
    expected_type: Optional[str] = None,
) -> None:
    if not isinstance(question, dict):
        issues.append(f"{path} 必须是对象")
        return

    missing_keys = QUESTION_REQUIRED_KEYS - set(question.keys())
    if missing_keys:
        issues.append(f"{path} 缺少字段：{', '.join(sorted(missing_keys))}")
        return

    q_type = question.get("type")
    if q_type not in ALLOWED_QUESTION_TYPES:
        issues.append(f"{path}.type 必须是合法题型枚举")
        return
    if expected_type and q_type != expected_type:
        issues.append(f"{path}.type 必须是 {expected_type}，实际为 {q_type}")

    _require_string(question, "id", path, issues)
    _require_string(question, "stem", path, issues)
    _require_string(question, "difficulty", path, issues)
    if question.get("difficulty") not in ALLOWED_DIFFICULTIES:
        issues.append(f"{path}.difficulty 必须是 easy、medium 或 hard")
    _require_number(question, "score", path, issues, positive=True)
    _require_list(question, "knowledgePoints", path, issues)
    _require_list(question, "tags", path, issues)
    _require_list(question, "sourceBasis", path, issues)
    _require_string(question, "analysis", path, issues, allow_empty=True)
    body = _require_object(question, "body", path, issues)
    answer = _require_object(question, "answer", path, issues)
    scoring = _require_object(question, "scoring", path, issues)
    if body is None or answer is None or scoring is None:
        return

    if not question.get("knowledgePoints"):
        warnings.append(f"{path}.knowledgePoints 为空，建议保留知识点以便检索和组卷")
    if not question.get("sourceBasis"):
        warnings.append(f"{path}.sourceBasis 为空，AI 生成题建议保留生成依据")

    _review_scoring(question, path, issues)
    validator = TYPE_VALIDATORS.get(q_type)
    if validator:
        validator(question, path, issues, warnings)


def _review_scoring(question: Dict[str, Any], path: str, issues: List[str]) -> None:
    score = _as_number(question.get("score"))
    scoring = question.get("scoring") or {}
    mode = scoring.get("mode")
    if mode not in ALLOWED_SCORING_MODES:
        issues.append(f"{path}.scoring.mode 必须是 exact、blank、rubric、step、program 或 manual")
        return
    rubrics = scoring.get("rubrics")
    if not isinstance(rubrics, list):
        issues.append(f"{path}.scoring.rubrics 必须是数组")
        return
    rubric_total = 0.0
    has_rubric_scores = False
    for index, rubric in enumerate(rubrics, start=1):
        if not isinstance(rubric, dict):
            issues.append(f"{path}.scoring.rubrics[{index}] 必须是对象")
            continue
        if "criterion" not in rubric or not isinstance(rubric.get("criterion"), str):
            issues.append(f"{path}.scoring.rubrics[{index}].criterion 必须是字符串")
        if "score" not in rubric or _as_number(rubric.get("score")) is None:
            issues.append(f"{path}.scoring.rubrics[{index}].score 必须是数字")
        else:
            has_rubric_scores = True
            rubric_total += float(rubric["score"])
    if mode in {"rubric", "step", "program", "manual"}:
        if not rubrics:
            issues.append(f"{path}.scoring.rubrics 在 {mode} 模式下不能为空")
        elif has_rubric_scores and score is not None and abs(rubric_total - score) > 0.001:
            issues.append(f"{path}.score 必须等于 scoring.rubrics 分值之和")


def _validate_single_choice(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    option_keys = _option_keys(question.get("body"), path, issues)
    correct = question.get("answer", {}).get("correctOption")
    if not isinstance(correct, str):
        issues.append(f"{path}.answer.correctOption 必须是字符串")
    elif option_keys and correct not in option_keys:
        issues.append(f"{path}.answer.correctOption 必须出现在 body.options.key 中")


def _validate_multiple_choice(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    option_keys = _option_keys(question.get("body"), path, issues)
    correct_options = question.get("answer", {}).get("correctOptions")
    if not isinstance(correct_options, list) or not correct_options:
        issues.append(f"{path}.answer.correctOptions 必须是非空数组")
        return
    for option in correct_options:
        if not isinstance(option, str):
            issues.append(f"{path}.answer.correctOptions 中每个值都必须是字符串")
        elif option_keys and option not in option_keys:
            issues.append(f"{path}.answer.correctOptions 的 {option} 未出现在 body.options.key 中")


def _validate_true_false(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("body", {}), "statement", f"{path}.body", issues)
    if not isinstance(question.get("answer", {}).get("correct"), bool):
        issues.append(f"{path}.answer.correct 必须是布尔值")


def _validate_fill_blank(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("body", {}), "text", f"{path}.body", issues)
    blank_ids = _blank_ids(question.get("body"), f"{path}.body", issues)
    answer_blanks = question.get("answer", {}).get("blanks")
    if not isinstance(answer_blanks, list) or not answer_blanks:
        issues.append(f"{path}.answer.blanks 必须是非空数组")
        return
    for index, item in enumerate(answer_blanks, start=1):
        if not isinstance(item, dict):
            issues.append(f"{path}.answer.blanks[{index}] 必须是对象")
            continue
        blank_id = item.get("id")
        if blank_id not in blank_ids:
            issues.append(f"{path}.answer.blanks[{index}].id 必须对应 body.blanks.id")
        answers = item.get("answers")
        if not isinstance(answers, list) or not all(isinstance(answer, str) for answer in answers):
            issues.append(f"{path}.answer.blanks[{index}].answers 必须是字符串数组")


def _validate_short_answer(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("answer", {}), "referenceAnswer", f"{path}.answer", issues)
    answer_points = question.get("answer", {}).get("answerPoints")
    if not isinstance(answer_points, list) or not all(isinstance(point, str) for point in answer_points):
        issues.append(f"{path}.answer.answerPoints 必须是字符串数组")


def _validate_essay(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("answer", {}), "referenceAnswer", f"{path}.answer", issues)
    key_points = question.get("answer", {}).get("keyPoints")
    if not isinstance(key_points, list) or not all(isinstance(point, str) for point in key_points):
        issues.append(f"{path}.answer.keyPoints 必须是字符串数组")


def _validate_material_analysis(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("body", {}), "material", f"{path}.body", issues)
    sub_questions = question.get("body", {}).get("subQuestions")
    if not isinstance(sub_questions, list) or not sub_questions:
        issues.append(f"{path}.body.subQuestions 必须是非空数组")
        return
    for index, sub_question in enumerate(sub_questions, start=1):
        _review_question(sub_question, f"{path}.body.subQuestions[{index}]", issues, warnings)


def _validate_calculation(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("answer", {}), "finalAnswer", f"{path}.answer", issues)
    steps = question.get("answer", {}).get("steps")
    if not isinstance(steps, list) or not all(isinstance(step, str) for step in steps):
        issues.append(f"{path}.answer.steps 必须是字符串数组")


def _validate_proof(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    proof_steps = question.get("answer", {}).get("proofSteps")
    if not isinstance(proof_steps, list) or not all(isinstance(step, str) for step in proof_steps):
        issues.append(f"{path}.answer.proofSteps 必须是字符串数组")
    _require_string(question.get("answer", {}), "conclusion", f"{path}.answer", issues)


def _validate_programming(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    body = question.get("body", {})
    answer = question.get("answer", {})
    for key in ("title", "description", "language", "inputFormat", "outputFormat"):
        _require_string(body, key, f"{path}.body", issues)
    language = str(body.get("language") or "").strip().lower()
    reference_solution = str(answer.get("referenceSolution") or "").strip()
    if reference_solution and language in {"未指定", "unknown", "unspecified", "n/a"}:
        issues.append(f"{path}.body.language 在提供参考代码时必须明确指定")
    _require_list(body, "constraints", f"{path}.body", issues)
    _require_list(body, "examples", f"{path}.body", issues)
    solution_outline = answer.get("solutionOutline")
    if not isinstance(solution_outline, list) or not all(isinstance(step, str) for step in solution_outline):
        issues.append(f"{path}.answer.solutionOutline 必须是字符串数组")
    _require_string(answer, "referenceSolution", f"{path}.answer", issues, allow_empty=True)
    test_cases = answer.get("testCases")
    if not isinstance(test_cases, list):
        issues.append(f"{path}.answer.testCases 必须是数组")
        return
    for index, test_case in enumerate(test_cases, start=1):
        if not isinstance(test_case, dict):
            issues.append(f"{path}.answer.testCases[{index}] 必须是对象")
            continue
        _require_string(test_case, "input", f"{path}.answer.testCases[{index}]", issues, allow_empty=True)
        _require_string(test_case, "expectedOutput", f"{path}.answer.testCases[{index}]", issues, allow_empty=True)
        if not isinstance(test_case.get("hidden"), bool):
            issues.append(f"{path}.answer.testCases[{index}].hidden 必须是布尔值")


def _validate_operation(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("body", {}), "task", f"{path}.body", issues)
    _require_list(question.get("body", {}), "requirements", f"{path}.body", issues)
    _require_string(question.get("answer", {}), "expectedResult", f"{path}.answer", issues)


def _validate_matching(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    left_keys = _item_keys(question.get("body", {}).get("leftItems"), f"{path}.body.leftItems", issues)
    right_keys = _item_keys(question.get("body", {}).get("rightItems"), f"{path}.body.rightItems", issues)
    pairs = question.get("answer", {}).get("pairs")
    if not isinstance(pairs, list) or not pairs:
        issues.append(f"{path}.answer.pairs 必须是非空数组")
        return
    for index, pair in enumerate(pairs, start=1):
        if not isinstance(pair, dict):
            issues.append(f"{path}.answer.pairs[{index}] 必须是对象")
            continue
        if pair.get("left") not in left_keys:
            issues.append(f"{path}.answer.pairs[{index}].left 必须出现在 body.leftItems.key 中")
        if pair.get("right") not in right_keys:
            issues.append(f"{path}.answer.pairs[{index}].right 必须出现在 body.rightItems.key 中")


def _validate_ordering(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    keys = _item_keys(question.get("body", {}).get("items"), f"{path}.body.items", issues)
    ordered_keys = question.get("answer", {}).get("orderedKeys")
    if not isinstance(ordered_keys, list) or not all(isinstance(key, str) for key in ordered_keys):
        issues.append(f"{path}.answer.orderedKeys 必须是字符串数组")
        return
    if set(ordered_keys) != keys:
        issues.append(f"{path}.answer.orderedKeys 必须与 body.items.key 完全一致")


def _validate_cloze(question: Dict[str, Any], path: str, issues: List[str], warnings: List[str]) -> None:
    _require_string(question.get("body", {}), "text", f"{path}.body", issues)
    _option_keys(question.get("body"), path, issues)
    blank_ids = _blank_ids(question.get("body"), f"{path}.body", issues)
    answer_blanks = question.get("answer", {}).get("blanks")
    if not isinstance(answer_blanks, list) or not answer_blanks:
        issues.append(f"{path}.answer.blanks 必须是非空数组")
        return
    for index, item in enumerate(answer_blanks, start=1):
        if not isinstance(item, dict):
            issues.append(f"{path}.answer.blanks[{index}] 必须是对象")
            continue
        if item.get("id") not in blank_ids:
            issues.append(f"{path}.answer.blanks[{index}].id 必须对应 body.blanks.id")
        if not isinstance(item.get("correctOption"), str):
            issues.append(f"{path}.answer.blanks[{index}].correctOption 必须是字符串")


TYPE_VALIDATORS = {
    "single_choice": _validate_single_choice,
    "multiple_choice": _validate_multiple_choice,
    "true_false": _validate_true_false,
    "fill_blank": _validate_fill_blank,
    "short_answer": _validate_short_answer,
    "essay": _validate_essay,
    "material_analysis": _validate_material_analysis,
    "calculation": _validate_calculation,
    "proof": _validate_proof,
    "programming": _validate_programming,
    "operation": _validate_operation,
    "matching": _validate_matching,
    "ordering": _validate_ordering,
    "cloze": _validate_cloze,
}


def _option_keys(body: Any, path: str, issues: List[str]) -> Set[str]:
    if not isinstance(body, dict):
        return set()
    options = body.get("options")
    if not isinstance(options, list) or not options:
        issues.append(f"{path}.body.options 必须是非空数组")
        return set()
    keys: Set[str] = set()
    for index, option in enumerate(options, start=1):
        if not isinstance(option, dict):
            issues.append(f"{path}.body.options[{index}] 必须是对象")
            continue
        key = option.get("key")
        text = option.get("text")
        if not isinstance(key, str) or not key:
            issues.append(f"{path}.body.options[{index}].key 必须是非空字符串")
        else:
            keys.add(key)
        if not isinstance(text, str) or not text:
            issues.append(f"{path}.body.options[{index}].text 必须是非空字符串")
    return keys


def _blank_ids(body: Any, path: str, issues: List[str]) -> Set[str]:
    if not isinstance(body, dict):
        return set()
    blanks = body.get("blanks")
    if not isinstance(blanks, list) or not blanks:
        issues.append(f"{path}.blanks 必须是非空数组")
        return set()
    ids: Set[str] = set()
    for index, blank in enumerate(blanks, start=1):
        if not isinstance(blank, dict):
            issues.append(f"{path}.blanks[{index}] 必须是对象")
            continue
        blank_id = blank.get("id")
        if not isinstance(blank_id, str) or not blank_id:
            issues.append(f"{path}.blanks[{index}].id 必须是非空字符串")
        else:
            ids.add(blank_id)
        _require_number(blank, "score", f"{path}.blanks[{index}]", issues, positive=True)
    return ids


def _item_keys(items: Any, path: str, issues: List[str]) -> Set[str]:
    if not isinstance(items, list) or not items:
        issues.append(f"{path} 必须是非空数组")
        return set()
    keys: Set[str] = set()
    for index, item in enumerate(items, start=1):
        if not isinstance(item, dict):
            issues.append(f"{path}[{index}] 必须是对象")
            continue
        key = item.get("key")
        text = item.get("text")
        if not isinstance(key, str) or not key:
            issues.append(f"{path}[{index}].key 必须是非空字符串")
        else:
            keys.add(key)
        if not isinstance(text, str) or not text:
            issues.append(f"{path}[{index}].text 必须是非空字符串")
    return keys


def _require_object(container: Dict[str, Any], key: str, path: str, issues: List[str]) -> Optional[Dict[str, Any]]:
    value = container.get(key)
    if not isinstance(value, dict):
        issues.append(f"{path}.{key} 必须是对象")
        return None
    return value


def _require_list(container: Dict[str, Any], key: str, path: str, issues: List[str]) -> None:
    if not isinstance(container.get(key), list):
        issues.append(f"{path}.{key} 必须是数组")


def _require_string(
    container: Dict[str, Any],
    key: str,
    path: str,
    issues: List[str],
    allow_empty: bool = False,
) -> None:
    value = container.get(key)
    if not isinstance(value, str):
        issues.append(f"{path}.{key} 必须是字符串")
        return
    if not allow_empty and not value.strip():
        issues.append(f"{path}.{key} 不能为空")


def _require_number(
    container: Dict[str, Any],
    key: str,
    path: str,
    issues: List[str],
    positive: bool = False,
) -> None:
    value = _as_number(container.get(key))
    if value is None:
        issues.append(f"{path}.{key} 必须是数字")
        return
    if positive and value <= 0:
        issues.append(f"{path}.{key} 必须大于 0")


def _as_number(value: Any) -> Optional[float]:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None
