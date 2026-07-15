from pathlib import Path
import re
from tempfile import TemporaryDirectory
import unittest

from scripts.agent_a3_document.source_loader import (
    EvidenceRow,
    extract_evidence_rows,
    load_source,
    parse_markdown,
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


class SourceContentTest(unittest.TestCase):
    SOURCE_DIR = Path("docs/project-document/source")
    SOURCE_NAMES = (
        "00-frontmatter.md",
        "01-project-overview.md",
        "02-requirements.md",
        "03-overall-design.md",
    )

    def _read_source(self, name: str) -> str:
        path = self.SOURCE_DIR / name
        self.assertTrue(path.is_file(), f"missing reviewed source: {path}")
        return path.read_text(encoding="utf-8")

    def _combined_source(self) -> str:
        return "\n".join(self._read_source(name) for name in self.SOURCE_NAMES)

    def _requirement_row(self, requirement_id: str) -> tuple[str, ...]:
        text = self._read_source("02-requirements.md")
        prefix = f"| {requirement_id} |"
        line = next(
            (candidate for candidate in text.splitlines() if candidate.startswith(prefix)),
            None,
        )
        self.assertIsNotNone(line, f"missing requirement row: {requirement_id}")
        return tuple(cell.strip() for cell in line.strip().strip("|").split("|"))

    def test_frontmatter_uses_exact_titles_and_document_governance(self):
        text = self._read_source("00-frontmatter.md")
        required_text = (
            "AgentA3 智慧校园个性化学习多智能体系统——需求、设计与开发说明书",
            "第十五届中国软件杯大赛——A组赛题",
            "A3-基于大模型的个性化资源生成与学习多智能体系统开发",
            "AgentA3 项目组",
            "V1.0",
            "2026-07-15",
            "修订记录",
            "摘要",
            "文档范围",
        )
        for value in required_text:
            with self.subTest(value=value):
                self.assertIn(value, text)
        self.assertEqual(text.count("<!-- AUTO_TOC -->"), 1)

    def test_sources_use_only_supported_directives_and_section_breaks(self):
        for name in self.SOURCE_NAMES:
            with self.subTest(name=name):
                text = self._read_source(name)
                blocks = parse_markdown(text, source_name=name)
                self.assertTrue(blocks)
                self.assertEqual(
                    sum(block.kind == "section_break" for block in blocks), 1
                )

    def test_requirements_have_exact_unique_ids_and_traceability_columns(self):
        text = self._read_source("02-requirements.md")
        functional_ids = re.findall(r"(?<![A-Z0-9-])FR-\d{3}(?![A-Z0-9-])", text)
        nonfunctional_ids = re.findall(
            r"(?<![A-Z0-9-])NFR-\d{3}(?![A-Z0-9-])", text
        )
        self.assertEqual(
            functional_ids, [f"FR-{index:03d}" for index in range(1, 25)]
        )
        self.assertEqual(
            nonfunctional_ids,
            [f"NFR-{index:03d}" for index in range(1, 11)],
        )
        traceability_header = (
            "| ID | 参与者（actor） | 触发条件（trigger） | "
            "系统行为（system behavior） | 验收证据（acceptance evidence） | "
            "当前状态（current status） |"
        )
        self.assertEqual(text.count(traceability_header), 2)

    def test_requirements_cover_roles_pain_points_and_capability_matrix(self):
        text = self._read_source("02-requirements.md")
        for role in ("ADMIN", "TEACHER", "STUDENT", "MERCHANT"):
            with self.subTest(role=role):
                self.assertIn(role, text)
        pain_point_ids = re.findall(r"(?<![A-Z0-9-])BP-\d{3}(?![A-Z0-9-])", text)
        self.assertEqual(pain_point_ids, [f"BP-{index:03d}" for index in range(1, 7)])
        capability_header = (
            "| 能力维度 | 通用聊天机器人 | 传统教学平台 | "
            "基础知识库问答 | AgentA3 |"
        )
        self.assertEqual(text.count(capability_header), 1)

    def test_current_entry_and_admin_only_permissions_are_explicit(self):
        requirements = self._read_source("02-requirements.md")
        design = self._read_source("03-overall-design.md")

        for role_row in (
            "| ADMIN | 管理员 | Web |",
            "| TEACHER | 教师 | App |",
            "| STUDENT | 学生 | App |",
            "| MERCHANT | 商户 | Web |",
        ):
            with self.subTest(role_row=role_row):
                self.assertIn(role_row, requirements)

        entry_fact = (
            "当前 App 登录入口允许 STUDENT 与 TEACHER；"
            "当前 Web 登录入口允许 ADMIN 与 MERCHANT。"
        )
        self.assertIn(entry_fact, requirements)
        self.assertIn(entry_fact, design)

        expected_rows = {
            "FR-002": ("学生、教师", "已实现（implemented）"),
            "FR-012": ("管理员", "已实现（implemented）"),
            "FR-013": (
                "管理员（当前）；学生、教师（目标）",
                "部分实现（partial）",
            ),
            "FR-014": (
                "管理员（当前）；学生、教师（目标）",
                "部分实现（partial）",
            ),
            "FR-018": (
                "管理员（当前）；教师（目标）",
                "部分实现（partial）",
            ),
            "FR-019": (
                "管理员（当前）；教师（目标）",
                "部分实现（partial）",
            ),
            "FR-020": (
                "管理员、商户（当前可进入）；教师（目标）",
                "当前限制（known-limit）",
            ),
            "FR-021": (
                "管理员、商户（当前可进入）；教师（目标）",
                "当前限制（known-limit）",
            ),
            "FR-024": (
                "管理员、商户（当前可登录）；教师（目标）",
                "当前限制（known-limit）",
            ),
        }
        for requirement_id, (actor, status_prefix) in expected_rows.items():
            with self.subTest(requirement_id=requirement_id):
                row = self._requirement_row(requirement_id)
                self.assertEqual(row[1], actor)
                self.assertTrue(row[5].startswith(status_prefix), row[5])

        for requirement_id in ("FR-012", "FR-013", "FR-014"):
            with self.subTest(requirement_id=requirement_id):
                self.assertIn("ADMIN", " ".join(self._requirement_row(requirement_id)))
        for requirement_id in ("FR-018", "FR-019"):
            with self.subTest(requirement_id=requirement_id):
                self.assertIn("ADMIN", " ".join(self._requirement_row(requirement_id)))

    def test_second_review_boundaries_are_explicit(self):
        requirements = self._read_source("02-requirements.md")
        design = self._read_source("03-overall-design.md")

        paper_auth_fact = (
            "当前 Web 路由与导航未按角色过滤；试卷创建、列表、详情与预览"
            "只校验登录，只有发布和取消发布执行 ADMIN 校验。"
        )
        self.assertIn(paper_auth_fact, requirements)
        self.assertIn(paper_auth_fact, design)
        merchant_row = next(
            line
            for line in requirements.splitlines()
            if line.startswith("| MERCHANT |")
        )
        self.assertIn("当前授权缺口", merchant_row)
        self.assertIn("未来加固", merchant_row)

        retrieval_fact = (
            "MaxKB 仅执行 hit-test 检索；Java 提取引用并组装 grounded context；"
            "系统 LLM/agent 生成最终回答，Java 再组合 citations 响应。"
        )
        self.assertIn(retrieval_fact, requirements)
        self.assertIn(retrieval_fact, design)

        python_boundary_terms = (
            "Python 会反向调用 Java，并转发原始 Authorization",
            "Java base URL 当前固定为 `http://localhost:8080`",
            "Python 记忆服务直接尝试 `redis://localhost:6379/0`",
            "Redis 不可用时退化为进程内存",
            "本地默认地址尚未环境配置",
        )
        for term in python_boundary_terms:
            with self.subTest(term=term):
                self.assertIn(term, design)

        cancellation_fact = (
            "服务端取消接口属于设计要求；当前客户端 AbortController 仅中止本地 fetch，"
            "不能证明存在 REST 取消端点。"
        )
        self.assertIn(cancellation_fact, requirements)
        self.assertIn(cancellation_fact, design)

    def test_overall_design_names_components_flows_and_deployment_boundary(self):
        text = self._read_source("03-overall-design.md")
        required_terms = (
            "uni-app",
            "React/Vite",
            "Spring Boot",
            "FastAPI",
            "MySQL",
            "Redis",
            "MaxKB",
            "讯飞（Xfyun）",
            "REST",
            "SSE",
            "WebSocket",
            "Python AI 服务当前单独启动",
            "不作为独立公网 API",
        )
        for term in required_terms:
            with self.subTest(term=term):
                self.assertIn(term, text)

    def test_sources_separate_requirements_goals_facts_and_roadmap(self):
        text = self._combined_source()
        for heading in (
            "赛题要求",
            "设计目标",
            "当前实现",
            "部分实现",
            "后续规划",
        ):
            with self.subTest(heading=heading):
                self.assertIn(heading, text)
        for status in ("implemented", "partial", "planned", "known-limit"):
            with self.subTest(status=status):
                self.assertIn(status, text)

    def test_sources_exclude_banned_claims_placeholders_secrets_and_local_paths(self):
        text = self._combined_source()
        banned_claims = (
            "所有智能体回答均经过 RAG",
            "30 个智能体同时自主协商",
            "考试结果已经自动更新七维画像",
            "已完成完整的动态学习路径管理",
            "所有资源包均包含视频或完整 PPTX",
            "一条 Compose 命令能够部署全部系统",
            "全部测试通过",
            "已通过等保",
        )
        forbidden_tokens = (
            "TBD",
            "TODO",
            "XXX",
            "X-X",
            "N 条样本",
            "待填写",
            "学校名称",
            "成员姓名",
            "/Users/",
            "/home/",
            "C:\\Users\\",
            "BEGIN PRIVATE KEY",
            "AKIA",
        )
        for value in (*banned_claims, *forbidden_tokens):
            with self.subTest(value=value):
                self.assertNotIn(value, text)


if __name__ == "__main__":
    unittest.main()
