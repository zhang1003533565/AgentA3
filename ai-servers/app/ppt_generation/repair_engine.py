"""RepairEngine: detect → repair → re-validate loop for slide layouts.

Spec §44-§49:
- runs at most MAX_REPAIR_ROUNDS (default 5) rounds
- every round records {round, error, element, strategy} in repairHistory
- repairs change content first; geometry is only restored to the template
  snapshot, never re-arranged; font shrink is bounded by min_font_size
- never fails silently: a slide that cannot be fully repaired keeps its
  issues visible in the outcome for QA reporting/logging
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Mapping, Optional, Tuple

from app.ppt_generation.content_fitter import RewriteCallable, fit_text
from app.ppt_generation.layout_validator import ValidationIssue, validate_slide
from app.ppt_generation.template_model import SlideLayoutModel, TemplateElementModel

logger = logging.getLogger(__name__)

MAX_REPAIR_ROUNDS = max(1, int(os.getenv("PPT_MAX_REPAIR_ROUNDS") or 5))
# 每页修复的 LLM 重写预算：推理已默认关闭（~40s/次），仍要严格控制，
# 避免溢出元素多时每页多次 LLM 调用拖垮生成速度
MAX_LLM_CALLS_PER_SLIDE = max(0, min(2, int(os.getenv("PPT_MAX_LLM_CALLS_PER_SLIDE") or 1)))
GEOMETRY_TOLERANCE = 0.5

# 修复后仍需重跑校验的错误类型（其余错误视为一次修复即可）
_RECHECK_TYPES = {"TEXT_OVERFLOW", "ELEMENT_OVERLAP", "UNBALANCED_CARDS", "CONTENT_TOO_DENSE", "FONT_TOO_SMALL"}


@dataclass
class RepairOutcome:
    ui: Dict[str, Any]
    history: List[Dict[str, Any]] = field(default_factory=list)
    final_issues: List[ValidationIssue] = field(default_factory=list)
    status: str = "clean"  # clean | repaired | partial
    last_result: Any = None  # 最后一次 ValidationResult（密度/填充比等）

    @property
    def repair_count(self) -> int:
        # 一个回合可能同时压缩多个同组元素；对外报告“修复次数”时按
        # 回合计数，才能与 MAX_REPAIR_ROUNDS 的上限一致，避免一次页面
        # 处理被误报成超过上限。
        return len({entry.get("round") for entry in self.history})


class RepairEngine:
    def __init__(self, max_rounds: int = MAX_REPAIR_ROUNDS) -> None:
        self.max_rounds = max(1, int(max_rounds))

    def repair(
        self,
        ui_tree: Mapping[str, Any],
        model: SlideLayoutModel,
        llm_rewrite: Optional[RewriteCallable] = None,
    ) -> RepairOutcome:
        ui = dict(ui_tree)
        history: List[Dict[str, Any]] = []
        llm_budget = MAX_LLM_CALLS_PER_SLIDE
        changed = True

        for round_no in range(1, self.max_rounds + 1):
            result = validate_slide(ui, model)
            errors = [issue for issue in result.issues if issue.severity == "error"]
            if not errors:
                status = "repaired" if history else "clean"
                return RepairOutcome(ui=ui, history=history, final_issues=result.issues, status=status, last_result=result)

            round_actions: List[Tuple[str, str, str]] = []  # (error_type, element, strategy)
            for issue in errors:
                strategy = self._strategy_for(issue)
                applied = self._apply_strategy(
                    ui, model, issue, strategy, llm_rewrite, llm_budget
                )
                if applied:
                    llm_budget -= applied.get("llm_calls", 0)
                    round_actions.append((issue.error_type, issue.element_id, applied["strategy"]))

            if not round_actions:
                changed = False
            for error_type, element_id, strategy in round_actions:
                history.append({
                    "round": round_no,
                    "error": error_type,
                    "element": element_id,
                    "strategy": strategy,
                })
            if not changed:
                break

        final = validate_slide(ui, model)
        final_errors = [issue for issue in final.issues if issue.severity == "error"]
        status = "partial" if final_errors else ("repaired" if history else "clean")
        if final_errors:
            logger.warning(
                "PPT slide 修复未完全成功 rounds=%d errors=%s history=%s",
                len(history),
                [(issue.error_type, issue.element_id) for issue in final_errors],
                history,
            )
        return RepairOutcome(ui=ui, history=history, final_issues=final.issues, status=status, last_result=final)

    def _strategy_for(self, issue: ValidationIssue) -> str:
        mapping = {
            "TEXT_OVERFLOW": "content-fit",
            "FONT_TOO_SMALL": "content-reduce",
            "ELEMENT_OVERLAP": "content-fit",
            "UNBALANCED_CARDS": "summarize",
            "CONTENT_TOO_DENSE": "content-reduce",
            "GEOMETRY_CHANGED": "restore-geometry",
            "OUT_OF_BOUNDS": "restore-geometry",
        }
        return mapping.get(issue.error_type, "content-fit")

    def _apply_strategy(
        self,
        ui: Dict[str, Any],
        model: SlideLayoutModel,
        issue: ValidationIssue,
        strategy: str,
        llm_rewrite: Optional[RewriteCallable],
        llm_budget: int,
    ) -> Optional[Dict[str, Any]]:
        if strategy == "restore-geometry":
            restored = _restore_element(ui, model, issue.element_id)
            return {"strategy": "restore-geometry", "llm_calls": 0} if restored else None

        if strategy in {"content-fit", "content-reduce", "summarize"}:
            # 先处理本元素，再处理同组件/同卡片组的其他长文本（卡片平衡）
            elements = _target_elements(model, issue)
            return _fit_element_contents(
                ui, model, elements, strategy, llm_rewrite, llm_budget
            )
        return None


def _target_elements(model: SlideLayoutModel, issue: ValidationIssue) -> List[Tuple[TemplateElementModel, int]]:
    """修复目标：报错元素的所有同名实例 + 同卡片组的全部可写文本元素。

    返回 (元素模型, 出现序号)，序号用于在树中定位同名实例。
    """
    targets: List[Tuple[TemplateElementModel, int]] = []
    for index, element in enumerate(model.occurrences(issue.element_id)):
        # 同名重复槽位的 dataclass 内容通常完全相同，不能用
        # ``element in targets`` 去重，否则第 2 个及之后的卡片会被漏掉，
        # 只有第一张卡被压缩，最终页面仍会被判定为溢出。
        targets.append((element, index))
    component_ids = {element.component_id for element, _ in targets if element.component_id}
    for component_id in component_ids:
        for group_id, members in model.card_groups.items():
            if group_id != component_id and not group_id.startswith(f"{component_id}:"):
                continue
            for name, index in members:
                element = model.element(name, index)
                if element and not any(
                    existing.name == name and existing_index == index
                    for existing, existing_index in targets
                ):
                    targets.append((element, index))
    return targets


def _fit_element_contents(
    ui: Dict[str, Any],
    model: SlideLayoutModel,
    elements: List[Tuple[TemplateElementModel, int]],
    strategy: str,
    llm_rewrite: Optional[RewriteCallable],
    llm_budget: int,
) -> Optional[Dict[str, Any]]:
    applied = False
    llm_calls = 0
    for element, index in elements:
        node = _find_node_by_name(ui, element.name, index)
        if node is None or element.constraint is None:
            continue
        text = _node_text(node)
        if not text.strip():
            continue
        if strategy == "summarize" and element.role not in {"card", "body"}:
            continue
        budget = max(0, llm_budget - llm_calls)
        current_font_size = _node_font_size(node)
        result = fit_text(
            text,
            element,
            llm_rewrite,
            llm_call_budget=min(2, budget),
            current_font_size=current_font_size,
        )
        text_changed = result.strategy != "none" and result.text != text
        font_changed = False
        if result.strategy == "shrink-font" and result.shrink_scale:
            font_changed = _scale_node_font(node, element, result.shrink_scale)
        if text_changed:
            _set_node_text(node, result.text)
        if text_changed or font_changed:
            applied = True
            if result.strategy in {"rewrite", "summarize"}:
                llm_calls += 1
    if not applied:
        return None
    return {"strategy": strategy, "llm_calls": llm_calls}


def _find_node_by_name(root: Mapping[str, Any], name: str, index: int = 0) -> Optional[Dict[str, Any]]:
    """返回树中第 index 个同名节点的真实引用（修复必须原地修改，不能改副本）。"""
    found: List[Dict[str, Any]] = []

    def walk(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                walk(item)
            return
        if not isinstance(node, Mapping):
            return
        if str(node.get("name") or "") == name:
            found.append(node)
        for key in ("components", "elements", "children"):
            if key in node:
                walk(node[key])
        if "child" in node:
            walk(node["child"])

    for key in ("components", "elements"):
        if key in root:
            walk(root[key])
    return found[index] if index < len(found) else None


def _node_text(node: Mapping[str, Any]) -> str:
    text = node.get("text")
    if isinstance(text, str) and text:
        return text
    runs = node.get("runs")
    if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
        return str(runs[0].get("text") or "")
    items = node.get("items")
    if isinstance(items, list):
        values = []
        for item in items:
            if isinstance(item, list):
                values.extend(str(run.get("text") or "") for run in item if isinstance(run, Mapping))
            elif isinstance(item, Mapping):
                values.append(str(item.get("text") or ""))
        return "\n".join(value for value in values if value)
    return ""


def _set_node_text(node: Dict[str, Any], text: str) -> None:
    if str(node.get("type") or "") == "text-list":
        values = [line.strip(" -*•") for line in str(text or "").splitlines() if line.strip(" -*•")]
        try:
            max_items = int(node.get("max_items") or 0)
        except (TypeError, ValueError):
            max_items = 0
        if max_items > 0:
            values = values[:max_items]
        font = dict(node.get("font") or {})
        node["items"] = [[{"text": value, "font": dict(font)}] for value in values]
        node["text"] = "\n".join(values)
        return
    node["text"] = text
    runs = node.get("runs")
    if isinstance(runs, list) and runs:
        for index, run in enumerate(runs):
            if isinstance(run, dict):
                run["text"] = text if index == 0 else ""


def _scale_node_font(node: Dict[str, Any], element: TemplateElementModel, scale: float) -> bool:
    changed = False

    def scale_font(font: Any) -> None:
        nonlocal changed
        if isinstance(font, dict):
            try:
                current = float(font.get("size") or 0)
            except (TypeError, ValueError):
                return
            if current > 0:
                next_size = round(max(current * scale, element.constraint.min_font_size), 1)
                if abs(next_size - current) > 0.05:
                    font["size"] = next_size
                    changed = True

    scale_font(node.get("font"))
    runs = node.get("runs")
    if isinstance(runs, list):
        for run in runs:
            if isinstance(run, dict):
                scale_font(run.get("font"))
    return changed


def _node_font_size(node: Mapping[str, Any]) -> Optional[float]:
    font = node.get("font")
    if isinstance(font, Mapping):
        try:
            return float(font.get("size") or 0) or None
        except (TypeError, ValueError):
            pass
    runs = node.get("runs")
    if isinstance(runs, list) and runs and isinstance(runs[0], Mapping):
        run_font = runs[0].get("font")
        if isinstance(run_font, Mapping):
            try:
                return float(run_font.get("size") or 0) or None
            except (TypeError, ValueError):
                pass
    return None


def _restore_element(ui: Dict[str, Any], model: SlideLayoutModel, name: str) -> bool:
    """把元素几何/字体/锁定文本还原为模板快照。

    树中的 position/size 是相对父容器的坐标，写回时必须换算成相对值
    （元素绝对坐标 - 父容器绝对基准），否则嵌套元素会被二次偏移。
    """
    element = model.element(name, 0)
    node = _find_node_by_name(ui, name, 0)
    if node is None or element is None:
        return False
    position = node.get("position")
    if isinstance(position, dict):
        position["x"] = round(element.x - element.parent_x, 1)
        position["y"] = round(element.y - element.parent_y, 1)
    size = node.get("size")
    if isinstance(size, dict):
        size["width"] = round(element.width, 1)
        size["height"] = round(element.height, 1)
    if element.element_type in {"text", "text-list"}:
        if element.font_size > 0:
            _restore_font(node, element)
        if element.locked and element.original_text:
            _set_node_text(node, element.original_text)
    return True


def _restore_font(node: Dict[str, Any], element: TemplateElementModel) -> None:
    def restore(font: Any) -> None:
        if isinstance(font, dict):
            font["size"] = element.font_size
            if element.font_family:
                font["family"] = element.font_family
            if element.color:
                font["color"] = element.color

    restore(node.get("font"))
    runs = node.get("runs")
    if isinstance(runs, list):
        for run in runs:
            if isinstance(run, dict):
                restore(run.get("font"))


__all__ = ["RepairEngine", "RepairOutcome", "MAX_REPAIR_ROUNDS"]
