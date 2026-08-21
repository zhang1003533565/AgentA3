"""ContentFitter: adapt AI content to an element's capacity.

Strategy order (spec §14, §15, §29) — content first, template last:

    rewrite → summarize → bulletize → remove-secondary
    → shrink-font (bounded, never below min_font_size) → ellipsis

The fitter never moves or resizes elements; when even bounded font shrink
cannot make the text fit it reports ``fits=False`` so the caller can flag
the slide instead of silently exporting a broken layout.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional

from app.ppt_generation.template_model import (
    TextConstraint,
    TemplateElementModel,
    count_content_chars,
    text_fits,
)

# LLM 重写调用签名：llm_rewrite(text, constraint, mode) -> Optional[str]
RewriteCallable = Callable[[str, TextConstraint, str], Optional[str]]


@dataclass
class FitResult:
    text: str
    strategy: str  # none | rewrite | summarize | bulletize | remove-secondary | shrink-font | ellipsis | failed
    fits: bool
    shrink_scale: Optional[float] = None
    actions: List[str] = field(default_factory=list)


REWRITE_SYSTEM_PROMPT = (
    "你是 PPT 模板的内容填充器，不是设计师。你只负责把内容压缩到指定容量。"
    "绝对不要编造新信息，不要输出解释、引号或 markdown 标记，只输出文本本身。"
)


def build_rewrite_user_prompt(text: str, constraint: TextConstraint, mode: str = "rewrite") -> str:
    goal = {
        "rewrite": "重写",
        "summarize": "总结",
    }.get(mode, "重写")
    lines = [
        f"请{goal}下面的内容，使其适配目标容量。",
        "",
        "原始内容：",
        text,
        "",
        "目标容量：",
        f"- 建议 {constraint.recommended_chars} 个汉字",
        f"- 绝对不得超过 {constraint.hard_max_chars} 个汉字",
        f"- 最多 {constraint.max_lines} 行",
        f"- 每行约 {constraint.chars_per_line:.0f} 字",
        "",
        "规则：",
        "- 必须保留核心结论与关键术语，删除背景解释、重复修饰词、次要例子",
        "- 不要编造任何数据或事实",
        "- 如果原始内容本身是多个要点，可合并为更少的要点",
        "- 直接输出结果文本",
    ]
    return "\n".join(lines)


def _split_points(text: str) -> List[str]:
    """把文本拆成要点列表：优先换行/项目符号，其次按句末标点切分。"""
    raw = str(text or "")
    lines = [line.strip().lstrip("•-*·").strip() for line in raw.splitlines()]
    points = [line for line in lines if line]
    if len(points) > 1:
        return points
    sentences = re.split(r"(?<=[。；!?！？])\s*", raw)
    points = [s.strip() for s in sentences if s.strip()]
    return points or [raw.strip()]


def _truncate_ellipsis(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    if max_chars <= 1:
        return text[:max_chars]
    return text[: max_chars - 1].rstrip() + "…"


def _bulletize(text: str, constraint: TextConstraint) -> str:
    """要点化：保留尽可能多的要点，每个要点不超过一行宽度。"""
    points = _split_points(text)
    per_line = max(1, int(constraint.chars_per_line) - 2)
    fitted = [_truncate_ellipsis(p, per_line) for p in points]
    kept = fitted[: constraint.max_lines]
    return "\n".join(f"• {p}" for p in kept)


def _remove_secondary(text: str, constraint: TextConstraint) -> str:
    """删次要内容：保留前 max_lines 个要点/句子。"""
    points = _split_points(text)
    kept = points[: constraint.max_lines]
    return "\n".join(f"• {p}" for p in kept)


def fit_text(
    text: str,
    element: TemplateElementModel,
    llm_rewrite: Optional[RewriteCallable] = None,
    llm_call_budget: int = 2,
    current_font_size: Optional[float] = None,
) -> FitResult:
    """按策略链压缩文本。llm_rewrite 为 None 时跳过重写/总结（纯程序化降级）。"""
    original = str(text or "").strip()
    if not original:
        return FitResult(text="", strategy="none", fits=True)
    constraint = element.constraint
    if constraint is None or text_fits(original, element, font_size=current_font_size):
        return FitResult(text=original, strategy="none", fits=True)

    actions: List[str] = []
    remaining = llm_call_budget
    # 轻微超限（≤1.3 倍硬上限）不值得一次 LLM 调用：程序化压缩即可，
    # 只有严重超限才动用 LLM 重写（每次约 40s，性能敏感）
    severe_overflow = len(original) > constraint.hard_max_chars * 1.3

    # 1) rewrite（LLM，明确目标容量）
    if llm_rewrite is not None and remaining > 0 and severe_overflow:
        remaining -= 1
        try:
            rewritten = (llm_rewrite(original, constraint, "rewrite") or "").strip()
        except Exception:
            rewritten = ""
        if rewritten and rewritten != original:
            actions.append("rewrite")
            if text_fits(rewritten, element, font_size=current_font_size):
                return FitResult(text=rewritten, strategy="rewrite", fits=True, actions=actions)

    # 2) summarize（LLM）
    if llm_rewrite is not None and remaining > 0 and severe_overflow and _looks_like_paragraph(original):
        remaining -= 1
        try:
            summarized = (llm_rewrite(original, constraint, "summarize") or "").strip()
        except Exception:
            summarized = ""
        if summarized and summarized != original:
            actions.append("summarize")
            if text_fits(summarized, element, font_size=current_font_size):
                return FitResult(text=summarized, strategy="summarize", fits=True, actions=actions)

    # 3) bulletize（程序化：按行宽截断要点；标题/标签不做要点化）
    if constraint.max_lines > 1 and element.role in {"body", "card"}:
        bulletized = _bulletize(original, constraint)
        actions.append("bulletize")
        if text_fits(bulletized, element, font_size=current_font_size):
            return FitResult(text=bulletized, strategy="bulletize", fits=True, actions=actions)

    # 4) remove-secondary（程序化：只保留前 max_lines 个要点；同样只用于正文/卡片）
    if element.role in {"body", "card"}:
        removed = _remove_secondary(original, constraint)
        actions.append("remove-secondary")
        if text_fits(removed, element, font_size=current_font_size):
            return FitResult(text=removed, strategy="remove-secondary", fits=True, actions=actions)

    # 5) shrink-font（限幅缩字：由调用方应用到节点）
    # 中文标题经常只是超过“首选字号下的字符容量”，并不代表内容必须
    # 被截断。先验证缩到角色下限后确实能放下，再保留完整标题；只有
    # 连下限字号都放不下时，才继续走后面的硬截断。
    if (
        constraint.allow_font_shrink
        and element.font_size > 0
    ):
        lines, max_width = _measured(original, element, font_size=current_font_size)
        # 这里求的是“需要保留的字号比例”，不是“超出的比例”。
        # 原实现使用 required/available，溢出时结果大于 1，随后被
        # min(1, ...) 截成 1，导致 shrink-font 永远不会实际缩字。
        base_font_size = current_font_size or element.font_size
        needed = 1.0
        if element.height > 0:
            line_h = base_font_size * (element.line_height if element.line_height >= 0.4 else 1.2)
            if lines * line_h > element.height:
                needed = min(needed, element.height / (lines * line_h))
        if element.width > 0:
            if max_width > element.width:
                needed = min(needed, element.width / max_width)
        scale = min(1.0, needed)
        if scale < 1.0:
            clamped = max(scale, constraint.max_shrink_ratio)
            # 标题的硬容量是按首选字号计算的。若只是轻微超过该容量，
            # 用“刚好够用”的字号会让中文标题仍然显得过大；优先落到
            # 标题下限，保持与模板原有视觉比例一致。
            if (
                element.role in {"title", "subtitle"}
                and count_content_chars(original) > constraint.hard_max_chars
            ):
                clamped = constraint.max_shrink_ratio
            if text_fits(original, element, font_size=base_font_size * clamped):
                actions.append(f"shrink-font(scale={clamped:.2f})")
                return FitResult(
                    text=original,
                    strategy="shrink-font",
                    fits=True,
                    shrink_scale=clamped,
                    actions=actions,
                )

    # 6) ellipsis（仅单行槽位）
    if constraint.allow_ellipsis and constraint.max_lines <= 1:
        ellipsized = _truncate_ellipsis(original, max(1, int(constraint.chars_per_line) - 1))
        actions.append("ellipsis")
        if text_fits(ellipsized, element, font_size=current_font_size):
            return FitResult(text=ellipsized, strategy="ellipsis", fits=True, actions=actions)

    # 7) 兜底：硬截断到硬上限（绝不撑破版式），标记失败由调用方上报
    hard = max(1, constraint.hard_max_chars)
    truncated = _truncate_ellipsis(original, hard)
    actions.append("hard-truncate")
    return FitResult(text=truncated, strategy="failed", fits=False, actions=actions)


def _looks_like_paragraph(text: str) -> bool:
    return len(text) > 40 and "\n" not in text.strip()


def _measured(text: str, element: TemplateElementModel, font_size: Optional[float] = None):
    from app.ppt_generation.template_model import measure_text

    effective_font_size = font_size or element.font_size or 12.0
    box_width = element.width if element.width > 0 else max(1.0, len(text) * effective_font_size * 0.55)
    lines, max_width = measure_text(text, effective_font_size, box_width, element.line_height)
    return lines, max_width


__all__ = ["FitResult", "fit_text", "build_rewrite_user_prompt", "REWRITE_SYSTEM_PROMPT", "RewriteCallable"]
