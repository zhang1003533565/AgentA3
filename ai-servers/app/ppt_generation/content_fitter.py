"""ContentFitter: adapt AI content to an element's capacity.

Strategy order (spec §14, §15, §29) — content first, template last:

    rewrite → summarize → bulletize → remove-secondary
    → shrink-font (bounded, never below min_font_size). Content is never
    silently clipped with an ellipsis or arbitrary prefix. If the bounded
    strategies cannot fit the complete result, the original text is returned
    with ``fits=False`` so the quality gate can block the export.

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
    ADAPTIVE_TEXT_ROLES,
    count_content_chars,
    text_fits,
)

# LLM 重写调用签名：llm_rewrite(text, constraint, mode) -> Optional[str]
RewriteCallable = Callable[[str, TextConstraint, str], Optional[str]]


@dataclass
class FitResult:
    text: str
    strategy: str  # none | rewrite | summarize | bulletize | remove-secondary | shrink-font | failed
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


def _bulletize(text: str, constraint: TextConstraint) -> str:
    """要点化：只改变结构，不截断任何单个要点。"""
    points = _split_points(text)
    kept = points[: constraint.max_lines]
    return "\n".join(f"• {p}" for p in kept)


def _remove_secondary(text: str, constraint: TextConstraint) -> str:
    """删次要内容：保留前 max_lines 个要点/句子。"""
    points = _split_points(text)
    kept = points[: constraint.max_lines]
    return "\n".join(f"• {p}" for p in kept)


def _semantic_body_variants(text: str) -> List[str]:
    """Return complete-clause body candidates for moderate overflow."""
    original = re.sub(r"\s+", " ", str(text or "")).strip()
    if not original:
        return []
    clauses = [
        part.strip()
        for part in re.split(r"(?<=[。；!?！？])\s*", original)
        if part.strip()
    ]
    if len(clauses) <= 1:
        return []
    variants: List[str] = []

    def add(value: str) -> None:
        candidate = re.sub(r"\s+", " ", value).strip()
        if candidate and candidate != original and candidate not in variants:
            variants.append(candidate)

    # Keep complete clauses only. The caller performs the actual geometry
    # check, so the longest fitting candidate wins without arbitrary slicing.
    for count in range(len(clauses) - 1, 0, -1):
        add("".join(clauses[:count]))
    if len(clauses) >= 3:
        add("".join([clauses[0], clauses[-1]]))
    return variants


def _semantic_title_variants(text: str, max_chars: int = 0) -> List[str]:
    """Generate loss-aware title candidates without adding an ellipsis."""
    original = re.sub(r"\s+", " ", str(text or "")).strip()
    variants: List[str] = []

    def add(candidate: str) -> None:
        candidate = re.sub(r"\s+", " ", str(candidate or "")).strip(" ：:，,。 ")
        if candidate and candidate != original and candidate not in variants:
            variants.append(candidate)

    for pattern in (
        r"\s*[（(][^）)]*[）)]",
        r"(?:的)?(?:分析|详解|概述|介绍|总结|研究|解读|探讨)$",
        r"(?:与|及)其(?:分析|实践|应用)$",
    ):
        candidate = re.sub(pattern, "", original).strip(" ：:，,。 ")
        add(candidate)

    # A colon usually separates the topic from the angle.  Add both complete
    # phrases, then let the token-compression pass below simplify each phrase
    # further.  This keeps a meaningful phrase such as “教学改革” instead of
    # taking an arbitrary visible prefix.
    phrase_seeds = [original]
    for separator in ("：", ":", "｜", "|"):
        if separator in original:
            phrase_seeds.extend(part.strip() for part in original.split(separator))
    for seed in phrase_seeds:
        add(seed)

    # Remove low-information modifiers and connective words only when the
    # title still needs compression. This keeps the subject and conclusion
    # instead of taking an arbitrary visible prefix.
    removable_tokens = (
        "Data-driven ", "data-driven ", "复杂", "整体", "主要", "相关",
        "关键", "核心", "实施", "的", "与", "和", "及", "从",
    )
    # Breadth-first removal gives combinations such as
    # “复杂系统的关键机制与实施路径” -> “系统关键机制实施路径”, while the
    # source order and words themselves remain intact.
    queue = list(phrase_seeds)
    seen = set(queue)
    while queue and len(seen) < 96:
        seed = queue.pop(0)
        for token in removable_tokens:
            if token not in seed:
                continue
            candidate = seed.replace(token, "", 1)
            candidate = re.sub(r"\s+", " ", candidate).strip(" ：:，,。 ")
            if not candidate or candidate in seen:
                continue
            seen.add(candidate)
            queue.append(candidate)
            add(candidate)

    # For Chinese titles with a leading topic qualifier, the suffix after the
    # first “的” is a complete phrase and is safer than a character prefix.
    suffix = re.sub(r"^.+?的", "", original, count=1).strip()
    if suffix and suffix != original:
        add(suffix)
    if re.search(r"[\u4e00-\u9fff]", original):
        latin_stripped = re.sub(r"^[A-Za-z0-9][A-Za-z0-9 ._-]*[：: ]+", "", original).strip()
        add(latin_stripped)
    if max_chars > 0:
        # A final bounded candidate is still semantic shortening (no marker
        # is appended). Prefer a complete phrase generated above; this only
        # applies when the source has no separable phrase left.
        for candidate in list(variants):
            if len(candidate) > max_chars:
                compact = re.sub(r"(?:分析|详解|概述|介绍|总结|研究|解读|探讨|内容)$", "", candidate).strip()
                if compact and len(compact) <= max_chars and compact not in variants:
                    variants.append(compact)
    return variants


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

        # Condense a moderately long paragraph before giving up. Extreme
        # content remains visible to the quality gate instead of being hidden.
        content_length = count_content_chars(original)
        moderate_limit = max(constraint.hard_max_chars * 2, constraint.hard_max_chars + 24)
        if content_length <= moderate_limit:
            for candidate in _semantic_body_variants(original):
                if text_fits(candidate, element, font_size=current_font_size):
                    actions.append("semantic-shorten-body")
                    return FitResult(
                        text=candidate,
                        strategy="summarize",
                        fits=True,
                        actions=actions,
                    )

    # Footer/supporting-note slots are summaries, not body containers. Keep
    # complete sentence/clause boundaries when the model gives them a little
    # too much text; arbitrary character clipping would hide facts mid-word
    # and make the generated content differ from the trace data.
    if element.semantic_role == "footer":
        for candidate in _footer_prefix_variants(original):
            if candidate != original and text_fits(candidate, element, font_size=current_font_size):
                actions.append("semantic-shorten-footer")
                return FitResult(text=candidate, strategy="summarize", fits=True, actions=actions)

    if element.semantic_role in ADAPTIVE_TEXT_ROLES:
        title_variants = _semantic_title_variants(original, constraint.hard_max_chars)
        for candidate in title_variants:
            if text_fits(candidate, element, font_size=current_font_size):
                actions.append("semantic-shorten-title")
                return FitResult(text=candidate, strategy="rewrite", fits=True, actions=actions)
        # A title box can be geometrically valid at its declared minimum
        # font even when the preferred font's line-height is one pixel too
        # tall.  Try the semantic candidates at that bounded size before
        # declaring the title unfit; this preserves a complete phrase without
        # falling back to an ellipsis.
        if constraint.allow_font_shrink and element.font_size > 0:
            base_font_size = current_font_size or element.font_size
            min_scale = max(
                constraint.max_shrink_ratio,
                constraint.min_font_size / max(base_font_size, 0.1),
            )
            min_font_size = base_font_size * min_scale
            for candidate in title_variants:
                if text_fits(candidate, element, font_size=min_font_size):
                    actions.append("semantic-shorten-title")
                    actions.append(f"shrink-font(scale={min_scale:.2f})")
                    return FitResult(
                        text=candidate,
                        # RepairEngine applies the scale only for this
                        # strategy; keep the semantic replacement in
                        # ``actions`` while making the visual change explicit.
                        strategy="shrink-font",
                        fits=True,
                        shrink_scale=min_scale,
                        actions=actions,
                    )

    # 5) shrink-font（限幅缩字：由调用方应用到节点）
    # 中文标题经常只是超过“首选字号下的字符容量”，并不代表内容必须
    # 被截断。先验证缩到角色下限后确实能放下，再保留完整标题；只有
    # 连下限字号都放不下时，才显式报告失败。
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
                element.semantic_role in ADAPTIVE_TEXT_ROLES
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

    # 6) 显式失败：保留完整输入，让 QA/质量门禁报告真实溢出位置。
    # 这里绝不能用省略号、前缀或 hard_max_chars 静默改写用户内容。
    return FitResult(text=original, strategy="failed", fits=False, actions=actions + ["content-unfit"])


def _footer_prefix_variants(text: str) -> List[str]:
    """Return progressively shorter complete clauses for a summary footer."""
    value = re.sub(r"\s+", " ", str(text or "")).strip()
    if not value:
        return []
    # Prefer sentence boundaries, then Chinese/English clause boundaries.
    boundaries = [m.end() for m in re.finditer(r"[^。！？.!?；;]+[。！？.!?；;]", value)]
    boundaries += [m.end() for m in re.finditer(r"[^，,、:：]+[，,、:：]", value)]
    candidates = {value[:end].strip() for end in boundaries if end < len(value)}
    return sorted(candidates, key=len, reverse=True)


def _looks_like_paragraph(text: str) -> bool:
    return len(text) > 40 and "\n" not in text.strip()


def _measured(text: str, element: TemplateElementModel, font_size: Optional[float] = None):
    from app.ppt_generation.template_model import measure_text

    effective_font_size = font_size or element.font_size or 12.0
    box_width = element.width if element.width > 0 else max(1.0, len(text) * effective_font_size * 0.55)
    lines, max_width = measure_text(text, effective_font_size, box_width, element.line_height)
    return lines, max_width


__all__ = ["FitResult", "fit_text", "build_rewrite_user_prompt", "REWRITE_SYSTEM_PROMPT", "RewriteCallable"]
