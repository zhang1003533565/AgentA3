from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.agent_a3_document.source_loader import (
    EvidenceRow,
    extract_evidence_rows,
    load_source,
)


def _write_sources(root: Path, first_body: str) -> None:
    for index in range(11):
        body = first_body if index == 0 else f"# 第{index + 1}章\n\n正文。\n"
        (root / f"{index:02d}-chapter.md").write_text(body, encoding="utf-8")


class SourceContractTest(unittest.TestCase):
    def _assert_rejects_figure_width(self, width_cm: str) -> None:
        body = (
            '<!-- FIGURE src="figure.png" caption="验证图" '
            f'width_cm="{width_cm}" -->\n'
        )
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_sources(root, body)
            with self.assertRaisesRegex(
                ValueError,
                r"00-chapter\.md:1: figure width_cm must be a positive number",
            ):
                load_source(root)

    def test_loads_required_block_types_in_numeric_order(self):
        chapter_names = ["第一章 项目概述"] + [
            f"第{i}章 验证章节" for i in range(2, 11)
        ] + ["附录"]
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            for index, chapter in enumerate(chapter_names):
                body = (
                    f'<!-- SECTION_BREAK chapter="{chapter}" -->\n'
                    f"# {chapter}\n\n正文。\n\n"
                    '<!-- FIGURE src="figure.png" caption="验证图" '
                    'width_cm="15.5" -->\n\n'
                    "| 字段 | 内容 |\n|---|---|\n| 状态 | 已验证 |\n"
                )
                (root / f"{index:02d}-chapter.md").write_text(
                    body, encoding="utf-8"
                )
            blocks = load_source(root)
        kinds = {block.kind for block in blocks}
        self.assertTrue(
            {"heading", "paragraph", "table", "figure", "section_break"}
            <= kinds
        )
        chapters = [b.text for b in blocks if b.kind == "section_break"]
        self.assertEqual(chapters[0], "第一章 项目概述")
        self.assertEqual(chapters[-1], "附录")

    def test_loads_every_approved_block_kind(self):
        body = """<!-- AUTO_TOC -->
<!-- SECTION_BREAK chapter="第一章 项目概述" -->
# 第一章 项目概述

正文第一行。
正文第二行。

- 一级项目
  - 二级项目
1. 第一项
2. 第二项

```python
print("ok")
```

<!-- FIGURE src="images/overview.png" caption="总体图" width_cm="15.5" -->

| 字段 | 内容 |
|---|---|
| 状态 | 已验证 |

<!-- CALLOUT type="risk" title="当前限制" -->
第一段。

第二段。
<!-- END_CALLOUT -->

<!-- PAGE_BREAK -->
"""
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_sources(root, body)
            blocks = load_source(root)

        self.assertEqual(
            {block.kind for block in blocks},
            {
                "heading",
                "paragraph",
                "bullet",
                "numbered",
                "table",
                "figure",
                "callout",
                "code",
                "toc",
                "page_break",
                "section_break",
            },
        )
        bullets = [block for block in blocks if block.kind == "bullet"]
        self.assertEqual(
            [(block.text, block.level) for block in bullets],
            [("一级项目", 1), ("二级项目", 2)],
        )
        code = next(block for block in blocks if block.kind == "code")
        self.assertEqual(code.text, 'print("ok")')
        self.assertEqual(code.attrs, {"language": "python"})
        callout = next(block for block in blocks if block.kind == "callout")
        self.assertEqual(callout.text, "第一段。\n\n第二段。")
        self.assertEqual(callout.attrs, {"type": "risk", "title": "当前限制"})

    def test_rejects_unknown_directive_with_source_location(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_sources(root, "<!-- UNKNOWN value=\"x\" -->\n")
            with self.assertRaisesRegex(
                ValueError, r"00-chapter\.md:1: unknown directive"
            ):
                load_source(root)

    def test_rejects_non_numeric_figure_width(self):
        self._assert_rejects_figure_width("wide")

    def test_rejects_zero_figure_width(self):
        self._assert_rejects_figure_width("0")

    def test_rejects_negative_figure_width(self):
        self._assert_rejects_figure_width("-1.5")

    def test_rejects_malformed_table_with_source_location(self):
        body = """# 第一章

| 字段 | 内容 |
|---|
| 状态 | 已验证 |
"""
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write_sources(root, body)
            with self.assertRaisesRegex(
                ValueError, r"00-chapter\.md:3: malformed table"
            ):
                load_source(root)

    def test_requires_exactly_eleven_chapter_sources(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "00-only.md").write_text("# 唯一章节\n", encoding="utf-8")
            with self.assertRaisesRegex(
                ValueError, r"expected 11 chapter sources, found 1"
            ):
                load_source(root)


class EvidenceIndexContractTest(unittest.TestCase):
    def test_extracts_rows_with_the_four_approved_statuses(self):
        statuses = ("implemented", "partial", "planned", "known-limit")
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "evidence-index.md"
            data_rows = "\n".join(
                f"| EV-{index:03d} | 结论 {index} | {status} | "
                f"`path/to/evidence-{index}.md` | 成品表述 {index} |"
                for index, status in enumerate(statuses, start=1)
            )
            path.write_text(
                "# 工程证据索引\n\n"
                "| ID | Claim | Status | Evidence | Final wording |\n"
                "|---|---|---|---|---|\n"
                f"{data_rows}\n",
                encoding="utf-8",
            )

            rows = extract_evidence_rows(path)

        self.assertEqual(
            rows,
            [
                EvidenceRow(
                    id=f"EV-{index:03d}",
                    claim=f"结论 {index}",
                    status=status,
                    evidence=f"`path/to/evidence-{index}.md`",
                    final_wording=f"成品表述 {index}",
                )
                for index, status in enumerate(statuses, start=1)
            ],
        )

    def test_rejects_absolute_evidence_paths(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "evidence-index.md"
            path.write_text(
                "| ID | Claim | Status | Evidence | Final wording |\n"
                "|---|---|---|---|---|\n"
                "| EV-001 | 不安全证据 | implemented | `/tmp/secret.txt` | "
                "不得使用 |\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                ValueError,
                r"evidence-index\.md:3: evidence paths must be repository-relative",
            ):
                extract_evidence_rows(path)

    def test_rejects_windows_drive_and_unc_evidence_paths(self):
        unsafe_paths = (
            "C:/Users/name/file.txt",
            r"C:\Users\name\file.txt",
            r"\\server\share\file.txt",
            "//server/share/file.txt",
        )
        for unsafe_path in unsafe_paths:
            with self.subTest(unsafe_path=unsafe_path), TemporaryDirectory() as tmp:
                path = Path(tmp) / "evidence-index.md"
                path.write_text(
                    "| ID | Claim | Status | Evidence | Final wording |\n"
                    "|---|---|---|---|---|\n"
                    f"| EV-001 | 不安全证据 | implemented | `{unsafe_path}` | "
                    "不得使用 |\n",
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(
                    ValueError,
                    r"evidence-index\.md:3: evidence paths must be "
                    r"repository-relative",
                ):
                    extract_evidence_rows(path)


if __name__ == "__main__":
    unittest.main()
