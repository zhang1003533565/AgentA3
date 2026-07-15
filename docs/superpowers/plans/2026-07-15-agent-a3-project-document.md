# AgentA3 Project Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate and visually verify a 70–78 page A4 DOCX/PDF titled《AgentA3 智慧校园个性化学习多智能体系统——需求、设计与开发说明书》from current repository evidence.

**Architecture:** Store reviewable chapter content as Markdown, generate diagrams as deterministic high-resolution PNG assets, and convert the source through a focused Python `python-docx` builder. Keep factual claims in an evidence index, run structural/privacy/claim audits, then render every DOCX/PDF page for visual QA.

**Tech Stack:** Bundled Python 3, `python-docx`, Pillow, `pypdf`, `pdfplumber`, LibreOffice renderer, Poppler, Markdown source files, unittest.

## Global Constraints

- Final page size is A4 portrait with 2.2 cm left/right, 2.3 cm top, and 2.0 cm bottom margins.
- Final page count must be between 70 and 78 inclusive.
- Use the `standard_business_brief` preset with the named `agent_a3_a4_technical_report` override from the approved spec.
- No new package or system dependency may be installed.
- Final facts must follow code > tests > committed design > README priority.
- Do not claim universal RAG, autonomous 30-agent negotiation, exam-to-profile feedback, completed dynamic learning paths, all-green tests, measured SLA, or production-grade security.
- Do not copy secrets, passwords, tokens, public IPs, personal data, local absolute paths, or raw `application.yml` credential values into deliverables.
- Runtime/design screenshots must be labeled accurately; static mockups must say“界面设计稿”.
- Use the bundled Python at `/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3`.
- Render DOCX with `/Users/zzs/.codex/plugins/cache/openai-primary-runtime/documents/26.709.11516/skills/documents/render_docx.py` and `TMPDIR=/private/tmp`.

---

## File Structure

### Content and evidence

- Create: `docs/project-document/evidence-index.md` — claim-to-source evidence index used by the appendix and audits.
- Create: `docs/project-document/source/00-frontmatter.md`
- Create: `docs/project-document/source/01-project-overview.md`
- Create: `docs/project-document/source/02-requirements.md`
- Create: `docs/project-document/source/03-overall-design.md`
- Create: `docs/project-document/source/04-detailed-functions.md`
- Create: `docs/project-document/source/05-core-technologies.md`
- Create: `docs/project-document/source/06-data-interfaces.md`
- Create: `docs/project-document/source/07-ui-interaction.md`
- Create: `docs/project-document/source/08-testing.md`
- Create: `docs/project-document/source/09-deployment-usage.md`
- Create: `docs/project-document/source/10-summary-appendices.md`

### Builder and QA

- Create: `scripts/agent_a3_document/__init__.py`
- Create: `scripts/agent_a3_document/source_loader.py` — Markdown block parser and custom directive parser.
- Create: `scripts/agent_a3_document/styles.py` — A4 geometry, Word styles, numbering, tables, headers and footers.
- Create: `scripts/agent_a3_document/diagrams.py` — deterministic PNG diagram renderer.
- Create: `scripts/agent_a3_document/docx_builder.py` — chapter assembly, TOC field, sections, figures, tables and captions.
- Create: `scripts/agent_a3_document/qa.py` — source, DOCX, PDF, privacy and claim audits.
- Create: `scripts/generate_agent_a3_project_document.py` — stable CLI entry point.
- Create: `scripts/tests/test_agent_a3_project_document.py` — structural and artifact tests.

### Assets and outputs

- Create: `docs/project-document/assets/generated/*.png` — 11 generated diagrams.
- Reuse: `docs/designs/profile-radar/profile-radar-design.png`
- Reuse: `docs/designs/ai-conversation/ai-conversation-design.png`
- Reuse: `docs/designs/meeting-home/meeting-home-design.png`
- Create: `output/docx/AgentA3-需求设计与开发说明书.docx`
- Create: `output/pdf/AgentA3-需求设计与开发说明书.pdf`
- Create: `tmp/agent_a3_document/render/` — internal page PNGs and render PDF.

---

### Task 1: Lock the evidence index and source contract

**Files:**
- Create: `docs/project-document/evidence-index.md`
- Create: `scripts/agent_a3_document/__init__.py`
- Create: `scripts/agent_a3_document/source_loader.py`
- Test: `scripts/tests/test_agent_a3_project_document.py`

**Interfaces:**
- Produces: `load_source(source_dir: Path) -> list[Block]`
- Produces: `extract_evidence_rows(path: Path) -> list[EvidenceRow]`
- `Block.kind` is one of `heading`, `paragraph`, `bullet`, `numbered`, `table`, `figure`, `callout`, `code`, `toc`, `page_break`, `section_break`.
- Custom directives are exactly:
  - `<!-- AUTO_TOC -->`
  - `<!-- PAGE_BREAK -->`
  - `<!-- SECTION_BREAK chapter="章名" -->`
  - `<!-- FIGURE src="path" caption="caption" width_cm="15.5" -->`
  - `<!-- CALLOUT type="note|risk|evidence" title="title" -->` followed by paragraphs and `<!-- END_CALLOUT -->`.

- [ ] **Step 1: Write source contract tests**

```python
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.agent_a3_document.source_loader import load_source


class SourceContractTest(unittest.TestCase):
    def test_loads_required_block_types_in_numeric_order(self):
        chapter_names = ["第一章 项目概述"] + [f"第{i}章 验证章节" for i in range(2, 11)] + ["附录"]
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            for index, chapter in enumerate(chapter_names):
                body = (
                    f'<!-- SECTION_BREAK chapter="{chapter}" -->\n'
                    f'# {chapter}\n\n正文。\n\n'
                    '<!-- FIGURE src="figure.png" caption="验证图" width_cm="15.5" -->\n\n'
                    '| 字段 | 内容 |\n|---|---|\n| 状态 | 已验证 |\n'
                )
                (root / f"{index:02d}-chapter.md").write_text(body, encoding="utf-8")
            blocks = load_source(root)
        kinds = {block.kind for block in blocks}
        self.assertTrue({"heading", "paragraph", "table", "figure", "section_break"} <= kinds)
        chapters = [b.text for b in blocks if b.kind == "section_break"]
        self.assertEqual(chapters[0], "第一章 项目概述")
        self.assertEqual(chapters[-1], "附录")
```

- [ ] **Step 2: Run the tests and verify the missing-module failure**

Run:

```bash
/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest scripts.tests.test_agent_a3_project_document -v
```

Expected: FAIL with `ModuleNotFoundError: scripts.agent_a3_document`.

- [ ] **Step 3: Implement the parser contract**

```python
@dataclass(frozen=True)
class Block:
    kind: str
    text: str = ""
    level: int = 0
    attrs: dict[str, str] = field(default_factory=dict)
    rows: tuple[tuple[str, ...], ...] = ()


def load_source(source_dir: Path) -> list[Block]:
    paths = sorted(source_dir.glob("[0-9][0-9]-*.md"))
    if len(paths) != 11:
        raise ValueError(f"expected 11 chapter sources, found {len(paths)}")
    return [block for path in paths for block in parse_markdown(path.read_text(encoding="utf-8"))]
```

Implement headings, paragraphs, real list blocks, fenced code, Markdown tables and the four approved directives. Reject unknown directives and malformed tables with the source filename and line number.

- [ ] **Step 4: Create the evidence index**

Use columns `ID | Claim | Status | Evidence | Final wording`. Populate at least 35 rows covering architecture, roles, 30 agents, seven profile dimensions, meeting ASR, MaxKB, resources, question bank, exam, database counts, deployment, tests, security and known limits. Every path must be repository-relative.

- [ ] **Step 5: Run the parser tests**

Expected: parser unit tests PASS against temporary fixtures; source completeness tests begin after Tasks 2–4 create the 11 real source files.

- [ ] **Step 6: Commit the parser and evidence boundary**

```bash
git add docs/project-document/evidence-index.md scripts/agent_a3_document scripts/tests/test_agent_a3_project_document.py
git commit -m "docs: 建立项目说明书证据索引与内容契约" \
  -m "Constraint: 说明书事实必须可追溯到当前仓库" \
  -m "Rejected: 将内容直接写入生成脚本 | 难以审阅和复用" \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: 新增结论必须同步维护证据索引" \
  -m "Tested: 内容解析单元测试" -m "Not-tested: 尚未生成最终文档" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 2: Draft front matter, overview, requirements and overall design

**Files:**
- Create: `docs/project-document/source/00-frontmatter.md`
- Create: `docs/project-document/source/01-project-overview.md`
- Create: `docs/project-document/source/02-requirements.md`
- Create: `docs/project-document/source/03-overall-design.md`

**Interfaces:**
- Consumes: directive contract from Task 1.
- Produces: `FR-001` through `FR-024`, `NFR-001` through `NFR-010`, role matrix, competitor capability matrix and acceptance criteria.

- [ ] **Step 1: Write the front matter source**

Include the exact document title, A3 contest title, `AgentA3 项目组`, V1.0, 2026-07-15, revision record, abstract, document scope and the exact marker `<!-- AUTO_TOC -->`. Do not create school/member placeholders.

- [ ] **Step 2: Write project overview**

Cover higher-education personalization, the evidence-driven closed-loop positioning, project scope and terms. Use competition-provided requirements as requirements context and repository evidence as implementation facts.

- [ ] **Step 3: Write requirements**

Create four roles, six business pain points, a capability comparison table, 24 functional requirements and 10 non-functional requirements. Each row must include `ID`, `actor`, `trigger`, `system behavior`, `acceptance evidence`, and `current status`.

- [ ] **Step 4: Write overall design**

Describe uni-app, React/Vite, Spring Boot and FastAPI responsibilities; MySQL, Redis, MaxKB and Xfyun connections; REST/SSE/WebSocket flows; and the separate Python deployment boundary.

- [ ] **Step 5: Run source audits**

Run:

```bash
/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest scripts.tests.test_agent_a3_project_document.SourceContentTest -v
```

Expected: all requirement IDs are unique, no banned claim is present, and no placeholder token is found.

- [ ] **Step 6: Commit the first four source sections**

```bash
git add docs/project-document/source/00-frontmatter.md docs/project-document/source/01-project-overview.md docs/project-document/source/02-requirements.md docs/project-document/source/03-overall-design.md
git commit -m "docs: 完成项目概述需求与总体设计底稿" \
  -m "Constraint: 需求和现状必须明确区分" \
  -m "Rejected: 沿用参考文档的无编号需求 | 无法建立测试追踪" \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: 所有需求编号必须保持唯一" \
  -m "Tested: 需求编号与禁用表述扫描" -m "Not-tested: 尚未执行视觉排版" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 3: Draft detailed functions and core technology chapters

**Files:**
- Create: `docs/project-document/source/04-detailed-functions.md`
- Create: `docs/project-document/source/05-core-technologies.md`

**Interfaces:**
- Consumes: `FR-*` and `NFR-*` IDs from Task 2.
- Produces: `FUNC-01` through `FUNC-11`, `TECH-01` through `TECH-08`, test mappings and figure directives.

- [ ] **Step 1: Write 11 detailed function modules**

For identity, Leader, profile, resources, meeting, MaxKB, question generation, paper generation, online exams, AI operations and campus-service overview, include goal, actor, trigger, precondition, main/alternative/error flow, postcondition, rules, APIs, entities, acceptance and test IDs.

- [ ] **Step 2: Write eight core technology sections**

Use exact current boundaries:

- Seven profile dimensions and evidence candidate/applied state.
- Leader routing of 30 executable agent packages, not simultaneous negotiation.
- Xfyun RTASR WebSocket and meeting post-processing.
- Java-side MaxKB retrieval and cited response generation.
- Resource envelope, grounding and integrity fields.
- Five Web question types while seven agent packages exist.
- One-time paper preview proof and preview/final consistency.
- Exam snapshot, autosave/version conflict, objective scoring and no short-answer auto-score.

- [ ] **Step 3: Add evidence and limitation callouts**

Every technology section needs one `evidence` callout and at least one explicit failure/limit paragraph. No source code line longer than 25 lines may be reproduced.

- [ ] **Step 4: Run fact-boundary tests**

Expected: no forbidden universal or production-grade claim; every `TECH-*` section maps to at least one evidence row.

- [ ] **Step 5: Commit the functional and technical source**

```bash
git add docs/project-document/source/04-detailed-functions.md docs/project-document/source/05-core-technologies.md
git commit -m "docs: 完成核心功能与技术链路说明底稿" \
  -m "Constraint: 核心技术只能描述当前真实实现" \
  -m "Rejected: 用智能体目录数量替代协作证据 | 无法证明运行链路" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: 技术章节必须保留失败策略和已知限制" \
  -m "Tested: 事实边界和证据映射检查" -m "Not-tested: 尚未生成图示" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 4: Draft data, UI, testing, deployment and appendices

**Files:**
- Create: `docs/project-document/source/06-data-interfaces.md`
- Create: `docs/project-document/source/07-ui-interaction.md`
- Create: `docs/project-document/source/08-testing.md`
- Create: `docs/project-document/source/09-deployment-usage.md`
- Create: `docs/project-document/source/10-summary-appendices.md`

**Interfaces:**
- Consumes: requirement/function/technology IDs and evidence index.
- Produces: key entity dictionaries, API tables, page inventory, test trace matrix, deployment instructions, open-source and AI Coding disclosures.

- [ ] **Step 1: Write data and API design**

State 62 JPA entities and 61 unique mapped table names. Detail AI session/resource, profile, meeting and exam tables; summarize remaining domains. Distinguish logical IDs from physical foreign keys. Group 52 controllers/338 mappings by domain and document only key endpoint contracts.

- [ ] **Step 2: Write UI and interaction design**

Use profile radar, AI conversation and meeting design images with `界面设计稿` captions. Explain SSE states, resource cards, download behavior, failure recovery, meeting ASR and exam autosave. Do not use `docs/images/app-run.png` as a product screenshot.

- [ ] **Step 3: Write test and validation chapter**

Record the current Java result as `324 tests / 0 failures / 1 environment error / 1 skipped`. Do not calculate a Python pass rate from cache. Include requirement traceability, targeted verification, visual Word-paper evidence and explicit untested gaps.

- [ ] **Step 4: Write deployment and usage**

Document MySQL 8, Redis 7, Java 21, React/Vite/Nginx, HBuilderX and separately started FastAPI. Describe credential names only. State that AI is not in current Compose/CI deployment.

- [ ] **Step 5: Write summary and appendices**

Include achievements, current limits, security hardening, direct dependencies and versions, license-review status, OmX AI Coding statement, GB/T 7714 references and repository-relative evidence paths.

- [ ] **Step 6: Run source completeness tests**

Expected: exactly 11 source files; all required chapter headings, disclosures and trace IDs are present; no secrets or absolute paths.

- [ ] **Step 7: Commit the remaining source**

```bash
git add docs/project-document/source/06-data-interfaces.md docs/project-document/source/07-ui-interaction.md docs/project-document/source/08-testing.md docs/project-document/source/09-deployment-usage.md docs/project-document/source/10-summary-appendices.md
git commit -m "docs: 补全数据测试部署与附录底稿" \
  -m "Constraint: 测试、部署和安全结论不得超出当前证据" \
  -m "Rejected: 将配置上限写成实测性能 | 会形成虚假验收结论" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: 新测试结果只在可复现后替换现有结论" \
  -m "Tested: 章节完整性与隐私扫描" -m "Not-tested: 尚未执行最终渲染" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 5: Generate the 11 diagrams

**Files:**
- Create: `scripts/agent_a3_document/diagrams.py`
- Create: `docs/project-document/assets/generated/*.png`
- Test: `scripts/tests/test_agent_a3_project_document.py`

**Interfaces:**
- Produces: `build_all_diagrams(output_dir: Path) -> list[Path]`.
- Every image is PNG, at least 1600 px wide, white/light background, and uses `#0F766E`, `#123B45`, `#4F6B7A`, `#B7791F`, `#F2F4F5` only.

- [ ] **Step 1: Write diagram manifest tests**

```python
EXPECTED_DIAGRAMS = {
    "system-context.png",
    "four-part-architecture.png",
    "deployment-boundary.png",
    "agent-capability-groups.png",
    "profile-evidence-flow.png",
    "meeting-asr-loop.png",
    "maxkb-grounding-flow.png",
    "resource-envelope.png",
    "question-paper-exam-loop.png",
    "core-entity-relations.png",
    "requirements-trace.png",
}
```

Assert exact names, PNG format, width >= 1600, height >= 850 and non-empty bounding box.

- [ ] **Step 2: Implement reusable drawing helpers**

Provide rounded boxes, connectors, arrowheads, wrapped CJK text, grouped containers, legends and title/subtitle helpers using Pillow. Load `/System/Library/Fonts/PingFang.ttc` when present and fall back to a bundled CJK font discovered by the runtime.

- [ ] **Step 3: Generate all diagrams deterministically**

The agent group diagram must total 30 packages. The profile diagram must show seven dimensions. The test trace diagram must use requirement/design/interface/test relationships, not fabricated pass metrics.

- [ ] **Step 4: Run diagram tests and inspect each PNG**

Expected: 11/11 tests PASS and all labels are legible at document width.

- [ ] **Step 5: Commit diagrams and generator**

```bash
git add scripts/agent_a3_document/diagrams.py docs/project-document/assets/generated scripts/tests/test_agent_a3_project_document.py
git commit -m "docs: 生成项目说明书统一技术图示" \
  -m "Constraint: 图示必须在 A4 版心内保持可读" \
  -m "Rejected: 复用风格不一致的历史架构图 | 会破坏视觉统一" \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: 后续图示必须复用同一颜色和字号系统" \
  -m "Tested: 十一张图尺寸与清晰度检查" -m "Not-tested: 尚未嵌入最终 DOCX" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 6: Implement the DOCX style engine and builder

**Files:**
- Create: `scripts/agent_a3_document/styles.py`
- Create: `scripts/agent_a3_document/docx_builder.py`
- Create: `scripts/generate_agent_a3_project_document.py`
- Test: `scripts/tests/test_agent_a3_project_document.py`

**Interfaces:**
- Produces: `build_document(source_dir: Path, evidence_path: Path, asset_dir: Path, output_path: Path) -> Path`.
- Produces CLI:

```text
generate_agent_a3_project_document.py \
  --source docs/project-document/source \
  --evidence docs/project-document/evidence-index.md \
  --assets docs/project-document/assets/generated \
  --output output/docx/AgentA3-需求设计与开发说明书.docx
```

- [ ] **Step 1: Write DOCX structure tests**

Assert A4 section geometry, margin values, required styles, title/heading fonts, at least 10 sections, at least 11 generated images, at least 20 tables, no empty table cells in required columns, and no external relationships to local files.

- [ ] **Step 2: Implement `styles.py`**

Create explicit Normal/Title/Subtitle/Heading 1–3/Caption/Code/Callout styles. Set East Asian and Latin fonts, paragraph spacing, line spacing, table borders/cell margins, real list numbering, running headers and centered PAGE fields. Add `w:updateFields=true`.

- [ ] **Step 3: Implement document sections**

Use separate sections for cover, front matter and each chapter. Suppress the cover header/footer, use upper-Roman page numbers for front matter, restart decimal numbering at 1 for Chapter 1, and update chapter text in each running header.

- [ ] **Step 4: Implement block rendering**

Render headings, paragraphs, lists, tables, figures, callouts, code and explicit breaks. Captions use chapter-aware counters generated by the builder. Copy design images into the DOCX package, never link them externally.

- [ ] **Step 5: Implement the CLI and build DOCX**

Run the exact CLI above. Expected: non-empty DOCX, no warnings about missing assets, and all structural tests PASS.

- [ ] **Step 6: Commit the builder**

```bash
git add scripts/agent_a3_document/styles.py scripts/agent_a3_document/docx_builder.py scripts/generate_agent_a3_project_document.py scripts/tests/test_agent_a3_project_document.py output/docx/AgentA3-需求设计与开发说明书.docx
git commit -m "docs: 生成项目需求设计与开发说明书 Word 初版" \
  -m "Constraint: Word 必须使用 A4、真实样式和可更新目录" \
  -m "Rejected: 手工拼接不可复现的 Word 文件 | 难以持续校验" \
  -m "Confidence: medium" -m "Scope-risk: moderate" \
  -m "Directive: 内容调整必须通过生成器重建文档" \
  -m "Tested: DOCX 结构与资源嵌入测试" -m "Not-tested: 尚未完成逐页视觉验收" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 7: Add artifact audits and render the final DOCX/PDF

**Files:**
- Create: `scripts/agent_a3_document/qa.py`
- Modify: `scripts/tests/test_agent_a3_project_document.py`
- Modify: `output/docx/AgentA3-需求设计与开发说明书.docx`
- Create: `output/pdf/AgentA3-需求设计与开发说明书.pdf`

**Interfaces:**
- Produces: `audit_source(...)`, `audit_docx(...)`, `audit_pdf(...)`, and `AuditReport(errors, warnings, metrics)`.
- `audit_pdf` must report page count, blank pages, too-small raster figures, missing chapter names and duplicate caption numbers.

- [ ] **Step 1: Write failing QA tests**

Test banned claims, placeholder tokens, credential-like values, absolute paths, duplicate `图 X-Y`/`表 X-Y`, page count outside 70–78, missing chapter titles and blank pages.

- [ ] **Step 2: Implement source, DOCX and PDF audits**

Use `python-docx` for OOXML structure, `pypdf` for page count, and `pdfplumber` for page text. Return non-zero CLI status when any error is present.

- [ ] **Step 3: Render DOCX**

Run:

```bash
TMPDIR=/private/tmp /Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
  /Users/zzs/.codex/plugins/cache/openai-primary-runtime/documents/26.709.11516/skills/documents/render_docx.py \
  output/docx/AgentA3-需求设计与开发说明书.docx \
  --output_dir tmp/agent_a3_document/render \
  --emit_pdf
```

Expected: `page-*.png` files and a non-empty render PDF.

- [ ] **Step 4: Review every rendered page**

Create contact sheets only for navigation, then inspect every page at original resolution. Fix all clipping, overlap, broken tables, missing CJK glyphs, poor page breaks, unreadable figures, header/footer errors and excessive blank gaps. Rebuild and re-render after each meaningful fix.

- [ ] **Step 5: Copy the verified PDF to the final output**

Use the renderer-produced PDF after the latest passing render as `output/pdf/AgentA3-需求设计与开发说明书.pdf`.

- [ ] **Step 6: Run final audits**

```bash
/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest scripts.tests.test_agent_a3_project_document -v
/Users/zzs/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m scripts.agent_a3_document.qa --docx output/docx/AgentA3-需求设计与开发说明书.docx --pdf output/pdf/AgentA3-需求设计与开发说明书.pdf
```

Expected: all tests PASS; QA reports 70–78 pages, zero errors, zero placeholders, zero banned claims and zero sensitive-value findings.

- [ ] **Step 7: Commit verified deliverables**

```bash
git add scripts/agent_a3_document/qa.py scripts/tests/test_agent_a3_project_document.py output/docx/AgentA3-需求设计与开发说明书.docx output/pdf/AgentA3-需求设计与开发说明书.pdf
git commit -m "docs: 交付项目说明书 Word 与 PDF 成品" \
  -m "Constraint: 成品必须逐页渲染且不泄露敏感信息" \
  -m "Rejected: 仅检查文本不检查渲染 | 无法发现裁切和分页缺陷" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: 修改内容后必须重跑全部结构与视觉校验" \
  -m "Tested: 单元测试、结构审计、隐私扫描与逐页视觉验收" \
  -m "Not-tested: 未在 Microsoft Word Windows 版执行二次打开验证" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 8: Final handoff verification

**Files:**
- Verify: `output/docx/AgentA3-需求设计与开发说明书.docx`
- Verify: `output/pdf/AgentA3-需求设计与开发说明书.pdf`

- [ ] **Step 1: Confirm clean final artifacts**

Check DOCX and PDF file sizes, SHA-256 hashes, page count, and successful opening by the renderer/PDF parser.

- [ ] **Step 2: Confirm repository scope**

Run `git status --short` and ensure unrelated user files remain untouched. Do not stage `docs/superpowers/plans/2026-07-15-python-personalized-learning-completion.md` unless it was independently requested.

- [ ] **Step 3: Prepare the final response**

Return the DOCX as the primary editable deliverable and the PDF as the submission-ready mirror. Report exact page count and the final QA result without linking internal PNG renders or scratch files.
