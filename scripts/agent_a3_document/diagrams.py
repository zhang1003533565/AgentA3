"""Deterministic Pillow diagrams for the AgentA3 project document."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from math import atan2, cos, pi, sin
from pathlib import Path
from typing import Callable, Iterable, Sequence

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1600
HEIGHT = 900

TEAL = "#0F766E"
DARK = "#123B45"
SLATE = "#4F6B7A"
AMBER = "#B7791F"
LIGHT = "#F2F4F5"
PALETTE = (TEAL, DARK, SLATE, AMBER, LIGHT)

SYSTEM_CONTEXT_EXTERNAL_CONNECTIONS = (
    ("Java", "讯飞 Xfyun ASR"),
    ("Python", "模型提供方"),
)
MEETING_RETURN_FLOW = (
    "Xfyun partial / final → Java handler",
    "Java → 客户端广播",
    "Java → MeetingRecord 持久化 final",
)
QUESTION_PAPER_GROUP = (45, 165, 1555, 500)
QUESTION_PAPER_FINAL_CARD = (1350, 245, 1550, 410)


@dataclass(frozen=True)
class TraceRow:
    requirements: str
    design: str
    interface: str
    test_id: str
    status: str


TRACE_STATUS_COLORS = {
    "implemented": TEAL,
    "partial": SLATE,
    "planned": AMBER,
    "known-limit": DARK,
}

# Conservative row status derived from the verification matrix and its explicit
# implementation limits in docs/project-document/source/08-testing.md.
TRACE_ROWS = (
    TraceRow("FR-001/002/024\nNFR-004", "FUNC-01", "API-01 · Role", "TC-01", "known-limit"),
    TraceRow("FR-003/004\nNFR-001/002", "FUNC-02 · TECH-02", "API-03\nAI Session / Task", "TC-02", "partial"),
    TraceRow("FR-005–008\nNFR-003", "FUNC-03 · TECH-01", "API-04/05/14/15\nProfile · Path", "TC-03", "implemented"),
    TraceRow("FR-015–017\nNFR-003", "FUNC-04 · TECH-06", "API-11–13\nTyped Resource DAG", "TC-04", "implemented"),
    TraceRow("FR-009–011\nNFR-006", "FUNC-05 · TECH-03/04", "API-06\nMeeting Record", "TC-05", "partial"),
    TraceRow("FR-012–014\nNFR-005", "FUNC-06 · TECH-05", "API-07\nKnowledge Chat", "TC-06", "partial"),
    TraceRow("FR-018/019\nNFR-007", "FUNC-07 · TECH-07", "API-08\nQuestion JSON", "TC-07", "partial"),
    TraceRow("FR-020/021\nNFR-003/004", "FUNC-08 · TECH-07", "API-09\nPreview Proof", "TC-08", "known-limit"),
    TraceRow("FR-022/023\nNFR-003", "FUNC-09 · TECH-08", "API-10\nlearningUpdate", "TC-09", "partial"),
    TraceRow("NFR-002/004/008/009", "FUNC-10 · TECH-03", "Config · WebSocket\nAuth", "TC-10", "known-limit"),
    TraceRow("NFR-005/006/010", "FUNC-11 · 总体部署", "MySQL / Redis / Java\nPython / Web / App", "TC-11", "partial"),
)


def _rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[index : index + 2], 16) for index in (0, 2, 4))


@lru_cache(maxsize=2)
def _discover_cjk_font(bold: bool = False) -> Path:
    candidates = [
        Path("/System/Library/Fonts/PingFang.ttc"),
        Path("/System/Library/Fonts/STHeiti Medium.ttc")
        if bold
        else Path("/System/Library/Fonts/STHeiti Light.ttc"),
        Path("/System/Library/Fonts/Hiragino Sans GB.ttc"),
        Path("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
        Path("/Library/Fonts/Arial Unicode.ttf"),
        Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc")
        if bold
        else Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        Path("/usr/share/fonts/opentype/source-han-sans/SourceHanSansSC-Bold.otf")
        if bold
        else Path("/usr/share/fonts/opentype/source-han-sans/SourceHanSansSC-Regular.otf"),
        Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
        Path("C:/Windows/Fonts/msyhbd.ttc") if bold else Path("C:/Windows/Fonts/msyh.ttc"),
        Path("C:/Windows/Fonts/simhei.ttf"),
    ]
    for candidate in candidates:
        if candidate.is_file():
            try:
                ImageFont.truetype(str(candidate), size=16)
            except OSError:
                continue
            return candidate

    search_roots = (
        Path("/System/Library/Fonts"),
        Path("/Library/Fonts"),
        Path.home() / "Library/Fonts",
        Path("/usr/share/fonts"),
        Path("/usr/local/share/fonts"),
        Path.home() / ".local/share/fonts",
        Path("C:/Windows/Fonts"),
    )
    patterns = (
        "*PingFang*.ttc",
        "*Hiragino*GB*.ttc",
        "*NotoSansCJK*.ttc",
        "*NotoSansCJK*.otf",
        "*SourceHanSans*.otf",
        "*wqy*.ttc",
        "*msyh*.ttc",
        "*simhei*.ttf",
        "*simsun*.ttc",
    )
    for root in search_roots:
        if root.is_dir():
            discovered = {
                candidate
                for pattern in patterns
                for candidate in root.rglob(pattern)
                if candidate.is_file()
            }
            for candidate in sorted(discovered):
                try:
                    ImageFont.truetype(str(candidate), size=16)
                except OSError:
                    continue
                return candidate
    raise RuntimeError(
        "No CJK-capable font found. Install PingFang, Noto Sans CJK, "
        "Source Han Sans, Microsoft YaHei, SimHei, or WenQuanYi Zen Hei."
    )


@lru_cache(maxsize=32)
def _font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(_discover_cjk_font(bold)), size=size)


class Diagram:
    """Small deterministic drawing surface with reusable document primitives."""

    def __init__(self, title: str, subtitle: str, *, height: int = HEIGHT) -> None:
        self.image = Image.new("RGB", (WIDTH, height), LIGHT)
        self.draw = ImageDraw.Draw(self.image)
        self.title(title, subtitle)

    def title(self, title: str, subtitle: str) -> None:
        self.draw.rounded_rectangle((54, 34, 76, 106), radius=10, fill=TEAL)
        self.draw.text((96, 31), title, font=_font(43, True), fill=DARK)
        self.draw.text((98, 86), subtitle, font=_font(22), fill=SLATE)
        self.draw.line((54, 130, 1546, 130), fill=SLATE, width=3)

    def wrapped_lines(self, text: str, font: ImageFont.ImageFont, width: int) -> list[str]:
        lines: list[str] = []
        for paragraph in text.split("\n"):
            if not paragraph:
                lines.append("")
                continue
            current = ""
            for char in paragraph:
                candidate = current + char
                if current and self.draw.textlength(candidate, font=font) > width:
                    lines.append(current.rstrip())
                    current = char.lstrip()
                else:
                    current = candidate
            if current:
                lines.append(current.rstrip())
        return lines

    def text(self, xy: tuple[int, int], text: str, width: int, *, size: int = 23,
             color: str = DARK, bold: bool = False, spacing: int = 7) -> int:
        font = _font(size, bold)
        lines = self.wrapped_lines(text, font, width)
        line_height = size + spacing
        x, y = xy
        for line in lines:
            self.draw.text((x, y), line, font=font, fill=color)
            y += line_height
        return y

    def box(self, rect: tuple[int, int, int, int], title: str, body: str = "", *,
            accent: str = TEAL, strong: bool = False, title_size: int = 27,
            body_size: int = 21, radius: int = 18, tag: str | None = None,
            tag_size: int = 17) -> None:
        x1, y1, x2, y2 = rect
        fill = DARK if strong else LIGHT
        text_color = LIGHT if strong else DARK
        self.draw.rounded_rectangle(rect, radius=radius, fill=fill, outline=accent, width=4)
        if not strong:
            self.draw.rounded_rectangle((x1, y1, x1 + 11, y2), radius=6, fill=accent)
        tag_reserved = 0
        if tag:
            tag_font = _font(tag_size, True)
            tag_width = int(self.draw.textlength(tag, font=tag_font)) + 28
            tag_height = tag_size + 17
            self.draw.rounded_rectangle(
                (x2 - tag_width - 12, y1 + 12, x2 - 12, y1 + 12 + tag_height),
                radius=13,
                fill=accent,
            )
            self.draw.text(
                (x2 - tag_width + 2, y1 + 15),
                tag,
                font=tag_font,
                fill=LIGHT,
            )
            tag_reserved = tag_width + 18
        title_width = x2 - x1 - 48 - tag_reserved
        title_font = _font(title_size, True)
        title_lines = self.wrapped_lines(title, title_font, title_width)
        y = y1 + 19
        for line in title_lines[:2]:
            self.draw.text((x1 + 28, y), line, font=title_font, fill=text_color)
            y += title_size + 5
        if body:
            y += 4
            self.text((x1 + 28, y), body, x2 - x1 - 52, size=body_size, color=text_color, spacing=5)

    def group(
        self,
        rect: tuple[int, int, int, int],
        label: str,
        *,
        color: str = SLATE,
        label_size: int = 21,
    ) -> None:
        x1, y1, x2, y2 = rect
        self.draw.rounded_rectangle(rect, radius=24, outline=color, width=4)
        font = _font(label_size, True)
        label_width = int(self.draw.textlength(label, font=font)) + 34
        self.draw.rectangle((x1 + 24, y1 - 15, x1 + 24 + label_width, y1 + 18), fill=LIGHT)
        self.draw.text((x1 + 41, y1 - 11), label, font=font, fill=color)

    def badge(
        self,
        xy: tuple[int, int],
        text: str,
        *,
        color: str = TEAL,
        size: int = 19,
    ) -> None:
        x, y = xy
        font = _font(size, True)
        width = int(self.draw.textlength(text, font=font)) + 30
        height = size + 17
        self.draw.rounded_rectangle(
            (x, y, x + width, y + height), radius=16, fill=color
        )
        self.draw.text((x + 15, y + 6), text, font=font, fill=LIGHT)

    def connector(self, points: Sequence[tuple[int, int]], label: str = "", *,
                  color: str = SLATE, width: int = 5, arrow: bool = True,
                  label_size: int = 18) -> None:
        self.draw.line(points, fill=color, width=width, joint="curve")
        if arrow and len(points) >= 2:
            x1, y1 = points[-2]
            x2, y2 = points[-1]
            angle = atan2(y2 - y1, x2 - x1)
            length = 15
            spread = pi / 6
            arrow_points = (
                (x2, y2),
                (x2 - length * cos(angle - spread), y2 - length * sin(angle - spread)),
                (x2 - length * cos(angle + spread), y2 - length * sin(angle + spread)),
            )
            self.draw.polygon(arrow_points, fill=color)
        if label:
            index = max(0, (len(points) - 2) // 2)
            ax, ay = points[index]
            bx, by = points[index + 1]
            mx, my = (ax + bx) // 2, (ay + by) // 2
            font = _font(label_size, True)
            label_width = int(self.draw.textlength(label, font=font)) + 20
            label_fill = AMBER if color == AMBER else LIGHT
            label_color = LIGHT if color == AMBER else color
            label_top = my - label_size - 10
            self.draw.rounded_rectangle(
                (mx - label_width // 2, label_top, mx + label_width // 2, my + 7),
                radius=10,
                fill=label_fill,
            )
            self.draw.text(
                (mx - label_width // 2 + 10, label_top + 4),
                label,
                font=font,
                fill=label_color,
            )

    def note(
        self,
        rect: tuple[int, int, int, int],
        title: str,
        body: str,
        *,
        title_size: int = 23,
        body_size: int = 19,
    ) -> None:
        self.box(
            rect,
            title,
            body,
            accent=AMBER,
            title_size=title_size,
            body_size=body_size,
        )

    def legend(
        self,
        items: Iterable[tuple[str, str]],
        y: int = 850,
        *,
        size: int = 18,
    ) -> None:
        x = 64
        for color, label in items:
            self.draw.rounded_rectangle((x, y, x + 24, y + 24), radius=6, fill=color)
            font = _font(size)
            self.draw.text((x + 34, y - 2), label, font=font, fill=DARK)
            x += 34 + int(self.draw.textlength(label, font=font)) + 42

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        palette_image = Image.new("P", (1, 1))
        raw_palette: list[int] = []
        for color in PALETTE:
            raw_palette.extend(_rgb(color))
        raw_palette.extend([0] * (768 - len(raw_palette)))
        palette_image.putpalette(raw_palette)
        quantized = self.image.quantize(palette=palette_image, dither=Image.Dither.NONE)
        quantized.convert("RGB").save(path, format="PNG", compress_level=9)


def _system_context(path: Path) -> None:
    d = Diagram("系统上下文与角色边界", "终端只访问 Java 业务边界；Python 与外部依赖均受控接入")
    java_source, xfyun_target = SYSTEM_CONTEXT_EXTERNAL_CONNECTIONS[0]
    python_source, model_target = SYSTEM_CONTEXT_EXTERNAL_CONNECTIONS[1]
    d.box((70, 170, 390, 325), "uni-app 移动端", "STUDENT · TEACHER\n校园服务 / AI / 会议 / 考试", tag="App")
    d.box((70, 410, 390, 580), "React / Vite Web", "ADMIN · MERCHANT\n路由导航未统一按角色过滤", accent=AMBER, tag="Web", body_size=20)
    d.box((555, 250, 1035, 500), "Spring Boot Java 业务治理", "认证与角色 · 领域状态 · JPA 持久化\n业务规则 · 外部服务编排 · 结果登记", strong=True, tag="主边界", body_size=24)
    d.box((590, 620, 1000, 805), "FastAPI Python AI 服务", "独立服务 · Leader 路由 · 专业智能体\n模型与资源生成 · /internal/* 内部契约", body_size=19, title_size=25)
    d.box((1190, 155, 1515, 275), "MySQL / Redis", "持久事实 / 短期状态", tag="数据", title_size=23, body_size=18)
    d.box((1190, 325, 1515, 455), "MaxKB", "仅 hit-test 检索\nJava 组装上下文与引用", tag="外部", title_size=23, body_size=18)
    d.box((1190, 505, 1515, 635), xfyun_target, "实时 ASR WebSocket\n由 Java 会议边界接入", tag="外部", title_size=22, body_size=18)
    d.box((1190, 690, 1515, 815), model_target, "模型推理\n由 Python AI 服务调用", tag="外部", title_size=23, body_size=18)
    d.connector(((390, 245), (555, 315)), "业务 API")
    d.connector(((390, 495), (555, 430)), "业务 API")
    d.connector(((1035, 300), (1190, 220)), "持久化 / 缓存")
    d.connector(((1035, 390), (1190, 390)), "hit-test")
    d.connector(((1035, 470), (1100, 470), (1100, 570), (1190, 570)), f"{java_source} → ASR")
    d.connector(((795, 500), (795, 620)), "内部 REST")
    d.connector(((1000, 715), (1100, 715), (1100, 755), (1190, 755)), f"{python_source} → 模型")
    d.legend(((TEAL, "已实现边界"), (AMBER, "当前限制"), (SLATE, "受控依赖")))
    d.save(path)


def _four_part_architecture(path: Path) -> None:
    d = Diagram("四部分总体架构", "交互、业务治理、模型执行分层；前端不直接访问 Python 或数据库")
    cards = (
        ((55, 185, 385, 520), "① 移动端", "uni-app\n\n学生 / 教师入口\n校园、AI、会议、考试\n\n只调用 Java 业务接口", TEAL),
        ((420, 185, 750, 520), "② Web 管理端", "React / Vite\n\n管理员 / 商户入口\n知识库、题库、试卷治理\n\n角色隔离仍需加固", AMBER),
        ((785, 185, 1150, 520), "③ Java 后端", "Spring Boot / JPA\n\n认证、事务、领域状态\nMySQL / Redis 与外部编排\n\n业务事实权威边界", DARK),
        ((1185, 185, 1545, 520), "④ Python AI", "FastAPI\n\nLeader + Catalog + Runner\n专业智能体与模型调用\n\n独立服务、内部接口", TEAL),
    )
    for rect, title, body, color in cards:
        d.box(rect, title, body, accent=color, strong=color == DARK, title_size=29, body_size=23)
    d.connector(((220, 520), (220, 590), (930, 590), (930, 520)), "仅经 Java 业务 API")
    d.connector(((585, 520), (585, 560), (1000, 560), (1000, 520)))
    d.connector(((1150, 350), (1185, 350)), "内部任务")
    d.group((210, 640, 1390, 805), "共享协作原则")
    d.box((245, 680, 585, 770), "状态事实 ≠ 生成内容", "Java 状态规则拥有最终写权", title_size=23, body_size=19)
    d.box((630, 680, 970, 770), "业务治理 ≠ 模型执行", "失败结果不登记为业务成功", title_size=23, body_size=19)
    d.box((1015, 680, 1355, 770), "内部服务 ≠ 公网入口", "/internal/* 不直接暴露终端", title_size=23, body_size=19)
    d.legend(((TEAL, "工程边界"), (DARK, "业务权威"), (AMBER, "授权缺口")))
    d.save(path)


def _deployment_boundary(path: Path) -> None:
    d = Diagram(
        "五服务提交拓扑与内部 AI 边界",
        "deploy/compose.submission.yml 与 CI 已完成静态校验；镜像构建、up -d 和在线验证仍未执行",
        height=1000,
    )
    d.group((45, 175, 1215, 790), "提交 Compose：MySQL · Redis · Java · Python AI · Web", color=TEAL)
    d.box((75, 235, 320, 365), "Web/Nginx", "React/Vite 构建\n静态站点与 Java API 代理", title_size=20, body_size=18)
    d.box((390, 220, 720, 390), "Java 21", "Spring Boot\nAI_PYTHON_BASE_URL\nX-AI-Internal-Token", strong=True, body_size=19)
    d.box((860, 220, 1180, 390), "Python FastAPI AI", "JAVA_BACKEND_BASE_URL\nREDIS_URL · /internal/*\n生产令牌必须非空", title_size=22, body_size=18)
    d.box((170, 585, 455, 715), "MySQL 8", "持久化业务事实", title_size=22)
    d.box((560, 585, 845, 715), "Redis 7", "缓存、工作流短期状态与记忆", title_size=22, body_size=18)
    d.box((1270, 235, 1535, 480), "外部服务", "Java：MaxKB\nJava：讯飞 ASR\nPython：模型提供方\n\n本次门禁未真实调用", accent=SLATE, title_size=23, body_size=18)
    d.box((65, 840, 650, 930), "HBuilderX / uni-app", "独立构建；保留校园功能，学习入口仅经 Java 公网 API", title_size=20, body_size=16)
    d.connector(((320, 300), (390, 300)), "HTTP", label_size=17)
    d.connector(((720, 300), (860, 300)), "内部 REST + Token", label_size=16)
    d.connector(((555, 390), (555, 480), (315, 480), (315, 585)), "JPA")
    d.connector(((650, 390), (650, 585)), "缓存")
    d.connector(((1020, 390), (1020, 520), (700, 520), (700, 585)), "工作流 / 记忆")
    d.connector(((720, 350), (790, 350), (790, 455), (1235, 455), (1235, 300), (1270, 300)), "MaxKB / ASR", label_size=17)
    d.connector(((1180, 345), (1235, 345), (1235, 420), (1270, 420)), "模型", label_size=18)
    d.badge((885, 690), "静态校验 PASS · 容器实启 not_run", color=AMBER, size=18)
    d.legend(((TEAL, "Compose 内"), (AMBER, "尚未实启验证"), (SLATE, "外部依赖")), y=952)
    d.save(path)


def _agent_capability_groups(path: Path) -> None:
    d = Diagram("专业智能体能力分组与单目标路由", "Catalog 固定登记 30 个实现包；Leader 每次选择一个目标交给 Runner 执行")
    d.box((85, 175, 365, 310), "用户任务", "意图 + 受控上下文", tag="输入")
    d.box((510, 165, 800, 320), "Leader", "识别意图并选择一个目标", strong=True, tag="1 target")
    d.box((950, 175, 1230, 310), "Runner", "按 Catalog 契约加载执行", tag="执行")
    d.box((1340, 175, 1530, 310), "目标包", "结构化结果", title_size=23)
    d.connector(((365, 245), (510, 245)), "task")
    d.connector(((800, 245), (950, 245)), "target_agent")
    d.connector(((1230, 245), (1340, 245)), "一次执行")
    groups = (
        ((55, 455, 325, 700), "路由与画像", "2 包", "Leader 1\n画像总结 1"),
        ((360, 455, 665, 700), "图表与图像", "9 包", "架构提示 1\n图表 / 导图 / 图片 8"),
        ((700, 455, 1005, 700), "教材与题目", "8 包", "教材知识 1\n题目智能体 7"),
        ((1040, 455, 1280, 700), "会议", "6 包", "总控 / 整理 / 总结\n分析 / 推荐 / 播报"),
        ((1315, 455, 1545, 700), "PPT", "5 包", "大纲 / 布局 / 审查\n图片 / 转 DOCX"),
    )
    for rect, title, count, body in groups:
        d.box(rect, title, f"{count}\n{body}", tag=count, title_size=25, body_size=21)
    d.box((450, 750, 1150, 855), "2 + 9 + 8 + 6 + 5 = 30 个实现包", "登记数量不表示 30 个智能体同时自主协商", accent=AMBER, title_size=27, body_size=18)
    d.save(path)


def _profile_evidence_flow(path: Path) -> None:
    d = Diagram("七维画像证据慢更新", "单条信号先进入 candidate；只有 Java 汇总应用后才成为 applied")
    d.box((55, 190, 285, 355), "证据信号", "AI 对话 / 会议\n资源互动 / 客观题反馈", title_size=23, body_size=19)
    d.box((355, 190, 610, 355), "候选证据", "candidate\n来源 / 方向\n置信度 / 幂等", title_size=24, body_size=18)
    d.box((680, 180, 950, 365), "Java 汇总规则", "去重 · 冲突 · 权重融合\n控制实际改分幅度", strong=True, tag="写权")
    d.box((1020, 190, 1255, 355), "已应用证据", "applied\n记录实际变化与原因", title_size=23, body_size=18)
    d.box((1320, 180, 1545, 365), "画像快照", "分数 / 置信度\n趋势 / 证据计数", title_size=23, body_size=18)
    for start, end in (((285, 272), (355, 272)), ((610, 272), (680, 272)), ((950, 272), (1020, 272)), ((1255, 272), (1320, 272))):
        d.connector((start, end))
    dimensions = ("校园行为", "专业课程", "学习目标", "资源偏好", "薄弱知识", "学习进度", "能力表现")
    x = 55
    for index, label in enumerate(dimensions):
        d.box((x, 535, x + 200, 630), f"{index + 1}. {label}", "独立维护", title_size=21, body_size=17)
        x += 215
    d.group((35, 490, 1565, 680), "七个画像维度", color=TEAL)
    d.box((880, 735, 1260, 845), "画像总结智能体", "只解释快照：强项、欠缺与补证建议", title_size=23, body_size=19)
    d.connector(((1545, 270), (1570, 270), (1570, 790), (1260, 790)))
    d.note((55, 735, 720, 845), "考试反馈已接入", "客观题终态按知识点幂等更新掌握度、生成画像 candidate 并重排路径；主观题仍不自动评分。")
    d.save(path)


def _meeting_asr_loop(path: Path) -> None:
    d = Diagram("会议实时 ASR 与会后顺序处理", "实时链路区分 partial / final；会后处理仅消费确认转写")
    d.box((65, 175, 350, 305), "会议客户端", "鉴权后建立 WebSocket\n上传音频帧", tag="Client")
    d.box((650, 165, 990, 320), "Java 会议边界", "会话权限 · 事件广播\n确认记录持久化", strong=True, tag="Server")
    d.box((1260, 165, 1535, 325), "讯飞 Xfyun ASR", "实时 ASR\n外部 WebSocket", tag="External", title_size=23, body_size=19)
    d.connector(((350, 225), (650, 225)), "音频帧")
    d.connector(((990, 225), (1260, 225)), "签名连接 / 帧")
    d.connector(((1260, 285), (990, 285)), "partial / final 返回")
    d.connector(((650, 285), (350, 285)), "Java 广播")
    d.connector(((820, 320), (820, 390)), "final 持久化")
    d.box((650, 390, 990, 500), "MeetingRecord", "确认 final 进入稳定记录\npartial 不升级为 final", tag="状态", title_size=23, body_size=18)
    d.group((55, 560, 1545, 750), "会议结束后的顺序处理（完整端到端覆盖仍有限）", color=TEAL)
    steps = (
        ("冻结 final 转写", "稳定输入"),
        ("转写整理", "说话人 / 格式"),
        ("会议总结", "观点 / 结论"),
        ("成员分析", "偏差 / 参与"),
        ("资源推荐", "候选资源"),
    )
    x = 80
    for index, (title, body) in enumerate(steps):
        d.box((x, 610, x + 260, 705), title, body, title_size=21, body_size=17)
        if index < len(steps) - 1:
            d.connector(((x + 260, 660), (x + 300, 660)))
        x += 300
    d.note((65, 785, 1535, 865), "已知限制", "外部稳定性仍需部署验证；断线不把 partial 提升为 final；当前不代表完整 RTC 或 TTS；Origin 白名单、限流与审计仍需加固。")
    d.save(path)


def _maxkb_grounding_flow(path: Path) -> None:
    d = Diagram("MaxKB 检索与系统回答分工", "MaxKB 只执行 hit-test；最终 answer 由系统 LLM / agent 在受控上下文中生成")
    boxes = (
        ((55, 245, 270, 390), "ADMIN 问题", "知识库 + 问题\n当前控制器授权", TEAL),
        ((330, 230, 575, 405), "Java 校验", "ADMIN 权限\n请求与异常治理", DARK),
        ((635, 230, 890, 405), "MaxKB hit-test", "命中片段 + 引用\n不生成最终回答", AMBER),
        ((950, 220, 1225, 415), "Java 构建上下文", "去空 / 来源提取\n裁剪受控上下文", TEAL),
        ((1285, 230, 1545, 405), "系统模型 / agent", "基于限定上下文\n生成最终 answer", DARK),
    )
    for rect, title, body, color in boxes:
        d.box(rect, title, body, accent=color, strong=color == DARK, title_size=24, body_size=20)
    for start, end in (
        ((270, 315), (330, 315)),
        ((575, 315), (635, 315)),
        ((890, 315), (950, 315)),
        ((1225, 315), (1285, 315)),
    ):
        d.connector((start, end))
    d.box((970, 565, 1320, 710), "Java 组合响应", "answer + citations\n引用关联实际命中片段", tag="响应", title_size=23, body_size=19)
    d.connector(((1415, 405), (1415, 635), (1320, 635)), "answer")
    d.connector(((760, 405), (760, 500), (920, 500), (920, 655), (970, 655)), "references 来源")
    d.note((55, 560, 700, 730), "无命中 / 资料不足 / 外部失败", "返回资料不足、受限回答或可诊断错误；不能用无来源内容伪装知识库结论。")
    d.note((55, 785, 1545, 865), "适用边界", "citations 提供依据线索但不保证推理正确；RAG 仅覆盖明确的知识库问答链路，不自动包围全部专业智能体回答。")
    d.save(path)


def _resource_envelope(path: Path) -> None:
    d = Diagram("六类资源 typed DAG、信封与受控导出", "类型化节点并行生成、统一复审；局部失败保留成功项并支持单项重试")
    d.box((55, 185, 300, 370), "课程工作流请求", "画像 · 掌握度 · 路径\n稳定 workflowId", tag="输入", title_size=23, body_size=19)
    d.box((365, 165, 735, 405), "六类 typed DAG", "讲解文档 · 思维导图 · 练习题\n代码实验 · 演示课件 · 拓展阅读", strong=True, tag="Python", title_size=26, body_size=20)
    d.box((800, 185, 1080, 370), "统一复审", "证据 · grounding\n内容安全 · 类型契约", tag="review", title_size=24, body_size=19)
    d.box((1145, 175, 1535, 385), "真实导出 + 资源信封", "附件 / 受控预览\ntype · source · integrity\ndisplay · actions", tag="交付", title_size=23, body_size=18)
    d.connector(((300, 275), (365, 275)))
    d.connector(((735, 275), (800, 275)))
    d.connector(((1080, 275), (1145, 275)))
    d.group((55, 505, 1545, 735), "登记、展示、下载与局部恢复")
    chain = (
        ("成功资源集合", "拒绝项不进入"),
        ("Java 关联登记", "用户 / 工作流"),
        ("客户端资源卡", "六状态可见"),
        ("下载再校验", "用户 / 登记 / 路径"),
        ("partial / retry", "仅重试失败类型"),
    )
    x = 80
    for index, (title, body) in enumerate(chain):
        d.box((x, 565, x + 260, 680), title, body, title_size=22, body_size=18, accent=AMBER if index == 3 else TEAL)
        if index < len(chain) - 1:
            d.connector(((x + 260, 622), (x + 300, 622)))
        x += 300
    d.note((55, 780, 1545, 860), "可信边界", "审核拒绝、空附件或导出失败只影响对应类型；成功资源与路径不被单项重试覆盖。信封不等于零幻觉证明。")
    d.save(path)


def _question_paper_exam_loop(path: Path) -> None:
    d = Diagram("题库、预览证明与考试学习反馈", "题目候选需人工确认；预览证明绑定最终试卷；客观题终态触发幂等学习更新", height=1100)
    d.group(QUESTION_PAPER_GROUP, "题目候选 → 试卷可信链", color=TEAL, label_size=25)
    top = (
        ("Python 题目包", "7 个实现包"),
        ("Java 接口", "当前暴露\n5 类题型"),
        ("JSON 候选", "解析与题型校验"),
        ("人工审查 / 导入", "确认后进入题库"),
        ("手工 / 随机组卷", "确定题目与版式"),
        ("真实预览", "签发一次性证明"),
        ("最终创建", "校验并消费证明"),
    )
    x = 60
    for index, (title, body) in enumerate(top):
        width = 200
        rect = QUESTION_PAPER_FINAL_CARD if index == len(top) - 1 else (x, 245, x + width, 410)
        d.box(rect, title, body, title_size=21, body_size=20, accent=AMBER if index == 1 else TEAL)
        if index < len(top) - 1:
            d.connector(((x + width, 327), (x + width + 15, 327)))
        x += 215
    d.group((45, 590, 1555, 900), "在线考试快照、版本与评分", color=SLATE, label_size=25)
    bottom = (
        ("考试尝试", "固定试卷快照"),
        ("自动保存", "答案版本单调递增"),
        ("交卷 / 到期", "锁定终态"),
        ("客观题评分", "按确定规则"),
        ("幂等学习反馈", "掌握度 / candidate / 路径"),
    )
    x = 55
    for index, (title, body) in enumerate(bottom):
        d.box((x, 660, x + 260, 810), title, body, title_size=25, body_size=21, accent=AMBER if index == 4 else SLATE)
        if index < len(bottom) - 1:
            d.connector(((x + 260, 735), (x + 300, 735)), color=SLATE)
        x += 300
    d.note((45, 940, 1555, 1060), "当前边界", "旧版本或终态写入被拒绝；同一考试反馈不重复改分或增版。主观题仍需人工或后续处理，不生成虚假自动反馈。", title_size=25, body_size=22)
    d.save(path)


def _core_entity_relations(path: Path) -> None:
    d = Diagram("赛题主线关键实体逻辑关系", "箭头表示业务逻辑 ID 关联，不等同于数据库已经建立物理外键", height=1050)
    d.badge((55, 145), "65 个 JPA 实体 / 64 个唯一表名", size=22)
    d.badge((1210, 145), "54 控制器 / 404 映射注解", color=SLATE, size=22)
    d.box((650, 200, 950, 330), "用户 / 角色", "身份与角色上下文", strong=True, title_size=28, body_size=22)
    d.group((45, 390, 490, 880), "AI 会话与资源", color=TEAL, label_size=25)
    d.box((80, 450, 275, 570), "AI 会话", "用户会话", title_size=24, body_size=20)
    d.box((300, 450, 455, 570), "AI 任务", "路由与终态", title_size=24, body_size=20)
    d.box((80, 680, 275, 800), "资源信封", "来源与完整性", title_size=24, body_size=20)
    d.box((300, 680, 455, 800), "互动 / 导出", "互动与登记", title_size=23, body_size=20)
    d.connector(((275, 510), (300, 510)))
    d.connector(((377, 570), (377, 680)))
    d.connector(((275, 740), (300, 740)))
    d.group((555, 390, 1045, 880), "画像、掌握度、路径与会议", color=TEAL, label_size=23)
    d.box((590, 450, 790, 570), "画像 / 掌握度", "七维 + 知识点", title_size=23, body_size=20)
    d.box((820, 450, 1010, 570), "证据 / 路径", "候选 / 已应用\n路径版本", title_size=23, body_size=18)
    d.connector(((820, 510), (790, 510)))
    d.box((590, 680, 790, 800), "会议会话", "会议聚合根", title_size=24, body_size=20)
    d.box((820, 650, 1010, 830), "成员 / 记录 / 结果", "参与者 / final\n会后结果", title_size=23, body_size=20)
    d.connector(((790, 740), (820, 740)))
    d.group((1110, 390, 1555, 880), "题库、试卷与考试", color=TEAL, label_size=25)
    d.box((1145, 450, 1340, 570), "题目", "题型结构", title_size=24, body_size=20)
    d.box((1365, 450, 1520, 570), "试卷", "题目集合", title_size=24, body_size=20)
    d.connector(((1340, 510), (1365, 510)))
    d.box((1145, 680, 1340, 800), "预览证明", "签名 / 一次消费", title_size=23, body_size=20)
    d.box((1365, 650, 1520, 830), "考试尝试 / 答案", "快照 / 版本 / 得分", title_size=22, body_size=19)
    d.connector(((1442, 570), (1442, 650)))
    d.connector(((1340, 740), (1365, 740)))
    d.connector(((800, 330), (800, 360), (250, 360), (250, 390)), "用户归属", label_size=22)
    d.connector(((800, 330), (800, 390)), "画像 / 会议归属", label_size=22)
    d.connector(((800, 330), (800, 360), (1340, 360), (1340, 390)), "试卷 / 尝试归属", label_size=22)
    d.note((45, 920, 1555, 1015), "约束", "用户 ID、会议 ID、试卷 ID、资源 ID 等字段表达逻辑归属；只有迁移脚本、数据库元数据或映射明确时，才能宣称物理外键。", title_size=25, body_size=21)
    d.save(path)


def _requirements_trace(path: Path) -> None:
    d = Diagram(
        "需求—设计—接口—测试追踪",
        "11 条责任链按工程证据标注状态；需求目标本身不等于已实现",
        height=1630,
    )
    headers = (
        (55, "需求 ID"),
        (430, "功能 / 技术设计 ID"),
        (835, "接口 / 数据 ID"),
        (1210, "测试追踪 ID"),
    )
    for x, label in headers:
        d.box((x, 165, x + 335, 235), label, "", strong=True, title_size=26)
    y = 270
    for row in TRACE_ROWS:
        status_color = TRACE_STATUS_COLORS[row.status]
        values = (row.requirements, row.design, row.interface, row.test_id)
        for column, value in enumerate(values):
            x = headers[column][0]
            d.box(
                (x, y, x + 335, y + 90),
                value,
                "",
                accent=status_color,
                title_size=23 if column < 3 else 27,
                tag=row.status if column == 3 else None,
                tag_size=18,
            )
            if column < 3:
                d.connector(
                    ((x + 335, y + 45), (headers[column + 1][0], y + 45)),
                    color=status_color,
                    width=4,
                )
        y += 101
    d.legend(
        (
            (TEAL, "implemented"),
            (SLATE, "partial"),
            (AMBER, "planned"),
            (DARK, "known-limit"),
        ),
        y=1400,
        size=23,
    )
    d.note(
        (55, 1470, 1545, 1585),
        "读取方式",
        "TC 编号只追踪验收责任与证据入口；每行颜色与状态标签来自保守的工程证据判定，不代表虚构通过率、QPS、准确率或 SLA。",
        title_size=26,
        body_size=22,
    )
    d.save(path)


BUILDERS: tuple[tuple[str, Callable[[Path], None]], ...] = (
    ("system-context.png", _system_context),
    ("four-part-architecture.png", _four_part_architecture),
    ("deployment-boundary.png", _deployment_boundary),
    ("agent-capability-groups.png", _agent_capability_groups),
    ("profile-evidence-flow.png", _profile_evidence_flow),
    ("meeting-asr-loop.png", _meeting_asr_loop),
    ("maxkb-grounding-flow.png", _maxkb_grounding_flow),
    ("resource-envelope.png", _resource_envelope),
    ("question-paper-exam-loop.png", _question_paper_exam_loop),
    ("core-entity-relations.png", _core_entity_relations),
    ("requirements-trace.png", _requirements_trace),
)


def build_all_diagrams(output_dir: Path) -> list[Path]:
    """Build the exact deterministic diagram manifest into ``output_dir``."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    generated: list[Path] = []
    for filename, builder in BUILDERS:
        path = output_dir / filename
        builder(path)
        generated.append(path)
    return generated


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    output_dir = root / "docs/project-document/assets/generated"
    for path in build_all_diagrams(output_dir):
        print(path.relative_to(root))


if __name__ == "__main__":
    main()
