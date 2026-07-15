"""Deterministic Pillow diagrams for the AgentA3 project document."""

from __future__ import annotations

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


def _rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[index : index + 2], 16) for index in (0, 2, 4))


@lru_cache(maxsize=32)
def _font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = (
        Path("/System/Library/Fonts/PingFang.ttc"),
        Path("/System/Library/Fonts/STHeiti Medium.ttc") if bold else Path("/System/Library/Fonts/STHeiti Light.ttc"),
        Path("/Library/Fonts/Arial Unicode.ttf"),
        Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc") if bold else Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
    )
    for candidate in candidates:
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size=size)

    search_roots = (
        Path("/usr/share/fonts"),
        Path("/usr/local/share/fonts"),
        Path.home() / ".local/share/fonts",
    )
    for root in search_roots:
        if root.is_dir():
            for candidate in sorted(root.rglob("*CJK*.tt[cf]")):
                return ImageFont.truetype(str(candidate), size=size)
    return ImageFont.load_default()


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
            body_size: int = 21, radius: int = 18, tag: str | None = None) -> None:
        x1, y1, x2, y2 = rect
        fill = DARK if strong else LIGHT
        text_color = LIGHT if strong else DARK
        self.draw.rounded_rectangle(rect, radius=radius, fill=fill, outline=accent, width=4)
        if not strong:
            self.draw.rounded_rectangle((x1, y1, x1 + 11, y2), radius=6, fill=accent)
        if tag:
            tag_font = _font(17, True)
            tag_width = int(self.draw.textlength(tag, font=tag_font)) + 28
            self.draw.rounded_rectangle((x2 - tag_width - 12, y1 + 12, x2 - 12, y1 + 42), radius=13, fill=accent)
            self.draw.text((x2 - tag_width + 2, y1 + 15), tag, font=tag_font, fill=LIGHT)
        title_width = x2 - x1 - 48 - (95 if tag else 0)
        title_font = _font(title_size, True)
        title_lines = self.wrapped_lines(title, title_font, title_width)
        y = y1 + 19
        for line in title_lines[:2]:
            self.draw.text((x1 + 28, y), line, font=title_font, fill=text_color)
            y += title_size + 5
        if body:
            y += 4
            self.text((x1 + 28, y), body, x2 - x1 - 52, size=body_size, color=text_color, spacing=5)

    def group(self, rect: tuple[int, int, int, int], label: str, *, color: str = SLATE) -> None:
        x1, y1, x2, y2 = rect
        self.draw.rounded_rectangle(rect, radius=24, outline=color, width=4)
        font = _font(21, True)
        label_width = int(self.draw.textlength(label, font=font)) + 34
        self.draw.rectangle((x1 + 24, y1 - 15, x1 + 24 + label_width, y1 + 18), fill=LIGHT)
        self.draw.text((x1 + 41, y1 - 11), label, font=font, fill=color)

    def badge(self, xy: tuple[int, int], text: str, *, color: str = TEAL) -> None:
        x, y = xy
        font = _font(19, True)
        width = int(self.draw.textlength(text, font=font)) + 30
        self.draw.rounded_rectangle((x, y, x + width, y + 36), radius=16, fill=color)
        self.draw.text((x + 15, y + 6), text, font=font, fill=LIGHT)

    def connector(self, points: Sequence[tuple[int, int]], label: str = "", *,
                  color: str = SLATE, width: int = 5, arrow: bool = True) -> None:
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
            font = _font(18, True)
            label_width = int(self.draw.textlength(label, font=font)) + 20
            self.draw.rounded_rectangle((mx - label_width // 2, my - 28, mx + label_width // 2, my + 5), radius=10, fill=LIGHT)
            self.draw.text((mx - label_width // 2 + 10, my - 24), label, font=font, fill=color)

    def note(self, rect: tuple[int, int, int, int], title: str, body: str) -> None:
        self.box(rect, title, body, accent=AMBER, title_size=23, body_size=19)

    def legend(self, items: Iterable[tuple[str, str]], y: int = 850) -> None:
        x = 64
        for color, label in items:
            self.draw.rounded_rectangle((x, y, x + 24, y + 24), radius=6, fill=color)
            self.draw.text((x + 34, y - 2), label, font=_font(18), fill=DARK)
            x += 34 + int(self.draw.textlength(label, font=_font(18))) + 42

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
    d.box((70, 175, 390, 335), "uni-app 移动端", "STUDENT · TEACHER\n校园服务 / AI / 会议 / 考试", tag="App")
    d.box((70, 430, 390, 605), "React / Vite Web", "ADMIN · MERCHANT\n路由导航未统一按角色过滤", accent=AMBER, tag="Web", body_size=20)
    d.box((555, 265, 1035, 515), "Spring Boot Java 业务治理", "认证与角色 · 领域状态 · JPA 持久化\n业务规则 · 外部服务编排 · 结果登记", strong=True, tag="主边界", body_size=24)
    d.box((590, 635, 1000, 815), "FastAPI Python AI 服务", "独立进程 · Leader 路由 · 专业智能体\n模型与资源生成 · /internal/* 内网契约", body_size=19, title_size=25)
    d.box((1190, 170, 1515, 315), "MySQL / Redis", "持久业务事实 / 短期状态", tag="数据")
    d.box((1190, 390, 1515, 555), "MaxKB", "仅 hit-test 检索\nJava 组装上下文与引用", tag="外部")
    d.box((1190, 645, 1515, 805), "讯飞 ASR / 模型提供方", "实时转写 / 模型推理\n凭据仅在服务端", tag="外部")
    d.connector(((390, 255), (555, 330)), "业务 API")
    d.connector(((390, 510), (555, 445)), "业务 API")
    d.connector(((1035, 335), (1190, 250)), "持久化 / 缓存")
    d.connector(((1035, 420), (1190, 470)), "hit-test")
    d.connector(((795, 515), (795, 635)), "内部 REST")
    d.connector(((1000, 730), (1110, 730), (1110, 720), (1190, 720)), "模型调用")
    d.legend(((TEAL, "已实现边界"), (AMBER, "当前限制"), (SLATE, "受控依赖")))
    d.save(path)


def _four_part_architecture(path: Path) -> None:
    d = Diagram("四部分总体架构", "交互、业务治理、模型执行分层；前端不直接访问 Python 或数据库")
    cards = (
        ((55, 185, 385, 520), "① 移动端", "uni-app\n\n学生 / 教师入口\n校园、AI、会议、考试\n\n只调用 Java 业务接口", TEAL),
        ((420, 185, 750, 520), "② Web 管理端", "React / Vite\n\n管理员 / 商户入口\n知识库、题库、试卷治理\n\n角色隔离仍需加固", AMBER),
        ((785, 185, 1150, 520), "③ Java 后端", "Spring Boot / JPA\n\n认证、事务、领域状态\nMySQL / Redis 与外部编排\n\n业务事实权威边界", DARK),
        ((1185, 185, 1545, 520), "④ Python AI", "FastAPI\n\nLeader + Catalog + Runner\n专业智能体与模型调用\n\n独立进程、内部接口", TEAL),
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
    d = Diagram("当前部署拓扑与独立 AI 服务边界", "Compose 覆盖 MySQL、Redis、Java 与 Web；Python FastAPI 需单独启动", height=1000)
    d.group((55, 175, 1085, 800), "AppBackend/docker-compose.yml 当前边界", color=TEAL)
    d.box((95, 235, 385, 360), "Web / Nginx", "React/Vite 构建\n静态站点与 Java API 代理", tag="Compose", title_size=23, body_size=18)
    d.box((480, 225, 865, 385), "Java 21", "Spring Boot\n认证、业务 API、JPA、外部服务治理", strong=True, tag="Compose", body_size=20)
    d.box((120, 590, 405, 720), "MySQL 8", "持久化业务事实", tag="Compose")
    d.box((515, 590, 800, 720), "Redis 7", "缓存与 Python 记忆", tag="Compose")
    d.box((1160, 205, 1535, 405), "Python FastAPI AI", "单独进程启动\n当前未纳入 Compose 与 CI", accent=AMBER, tag="独立部署", body_size=22)
    d.box((1160, 555, 1535, 745), "外部服务", "Java：MaxKB / 讯飞 ASR\nPython：模型提供方", tag="外部", body_size=21)
    d.box((65, 845, 505, 930), "HBuilderX / uni-app", "独立构建移动端；仅访问 Java 公网 API", title_size=21, body_size=17)
    d.connector(((385, 300), (480, 300)), "HTTP / SSE")
    d.connector(((675, 385), (675, 590)), "缓存")
    d.connector(((585, 385), (330, 590)), "JPA")
    d.connector(((865, 305), (1160, 305)), "内部 REST")
    d.connector(((1348, 405), (1348, 555)), "模型 / 工具")
    d.connector(((865, 350), (980, 350), (980, 630), (1160, 630)), "MaxKB / ASR")
    d.connector(((1160, 365), (1100, 365), (1100, 760), (760, 760), (760, 720)), "Python 记忆", color=AMBER)
    d.badge((810, 748), "Redis 不可用 → 进程内存", color=AMBER)
    d.legend(((TEAL, "Compose 内"), (AMBER, "独立部署 / 本地默认限制"), (SLATE, "外部依赖")), y=952)
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
    d.box((55, 190, 285, 355), "证据信号", "AI 对话 / 会议\n资源互动 / 资料事实", title_size=23, body_size=19)
    d.box((355, 190, 610, 355), "候选证据", "candidate\n来源 / 方向 / 置信度 / 幂等", title_size=24, body_size=18)
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
    d.note((55, 735, 720, 845), "考试链路当前边界", "交卷与客观评分不会自动写回七维画像；未来仍须经过显式映射与候选证据协议。")
    d.save(path)


def _meeting_asr_loop(path: Path) -> None:
    d = Diagram("会议实时 ASR 与会后顺序处理", "实时链路区分 partial / final；会后处理仅消费确认转写")
    d.box((65, 175, 350, 305), "会议客户端", "鉴权后建立 WebSocket\n上传音频帧", tag="Client")
    d.box((650, 165, 990, 320), "Java 会议边界", "会话权限 · 事件广播\n确认记录持久化", strong=True, tag="Server")
    d.box((1260, 165, 1535, 325), "讯飞 Xfyun ASR", "实时 ASR\n外部 WebSocket", tag="External", title_size=23, body_size=19)
    d.connector(((350, 225), (650, 225)), "音频帧")
    d.connector(((990, 225), (1260, 225)), "签名连接 / 帧")
    d.connector(((1260, 285), (1120, 285), (1120, 380), (990, 380)), "partial / final")
    d.connector(((650, 380), (500, 380), (500, 285), (350, 285)), "增量广播")
    d.box((650, 365, 990, 465), "MeetingRecord", "partial 临时展示；final 才进入稳定记录", tag="状态", title_size=23, body_size=18)
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
    d.connector(((760, 405), (760, 635), (970, 635)), "references")
    d.note((55, 545, 775, 730), "无命中 / 资料不足 / 外部失败", "返回资料不足、受限回答或可诊断错误；不能用无来源内容伪装知识库结论。")
    d.note((55, 785, 1545, 865), "适用边界", "citations 提供依据线索但不保证推理正确；RAG 仅覆盖明确的知识库问答链路，不自动包围全部专业智能体回答。")
    d.save(path)


def _resource_envelope(path: Path) -> None:
    d = Diagram("资源信封、互动与受控下载", "结构化元数据贯穿生成、登记、展示和下载校验")
    d.box((55, 200, 300, 345), "智能体候选内容", "任务标识 · 内容 · 声明类型", tag="生成")
    d.box((370, 180, 735, 385), "资源构建器", "标准化 Resource Envelope", strong=True, tag="Python")
    fields = ("type", "source", "grounding", "integrity", "display", "actions")
    x, y = 405, 280
    for index, field in enumerate(fields):
        d.badge((x + (index % 3) * 102, y + (index // 3) * 48), field, color=TEAL if index < 4 else SLATE)
    d.box((805, 200, 1085, 345), "Java 关联与登记", "用户 / 会话 / 任务\n持久化业务状态", tag="治理")
    d.box((1155, 200, 1515, 345), "客户端资源卡片", "按类型展示可用操作\n不猜测文件类型", tag="展示")
    d.connector(((300, 272), (370, 272)))
    d.connector(((735, 272), (805, 272)))
    d.connector(((1085, 272), (1155, 272)))
    d.group((55, 505, 1545, 735), "互动与导出链")
    chain = (
        ("点击 / 收藏 / 下载", "用户互动"),
        ("互动记录", "持久化"),
        ("exporter", "生成 + 登记"),
        ("下载再校验", "用户 / 登记 / 路径"),
        ("受控文件", "成功或明确失败"),
    )
    x = 80
    for index, (title, body) in enumerate(chain):
        d.box((x, 565, x + 260, 680), title, body, title_size=22, body_size=18, accent=AMBER if index == 3 else TEAL)
        if index < len(chain) - 1:
            d.connector(((x + 260, 622), (x + 300, 622)))
        x += 300
    d.note((55, 780, 1545, 860), "可信边界", "缺字段、integrity 失败、未登记或文件不可用时拒绝下载；资源信封不构成零幻觉证明，也不承诺每个资源包都含视频或完整 PPTX。")
    d.save(path)


def _question_paper_exam_loop(path: Path) -> None:
    d = Diagram("题库、预览证明与在线考试一致性", "题目候选需人工确认；预览证明绑定最终试卷；考试评分不自动写回画像")
    d.group((45, 165, 1555, 450), "题目候选 → 试卷可信链", color=TEAL)
    top = (
        ("Python 题目智能体", "7 个实现包"),
        ("Java 生成接口", "当前暴露 5 类题型"),
        ("结构化 JSON 候选", "解析与题型校验"),
        ("人工审查 / 导入", "确认后进入题库"),
        ("手工 / 随机组卷", "确定题目与版式"),
        ("真实预览", "签发一次性证明"),
        ("最终创建", "校验并消费证明"),
    )
    x = 65
    for index, (title, body) in enumerate(top):
        width = 200
        d.box((x, 235, x + width, 370), title, body, title_size=19, body_size=17, accent=AMBER if index == 1 else TEAL)
        if index < len(top) - 1:
            d.connector(((x + width, 303), (x + width + 20, 303)))
        x += 220
    d.group((45, 535, 1555, 795), "在线考试快照、版本与评分", color=SLATE)
    bottom = (
        ("ExamPaperAttempt", "固定试卷快照"),
        ("自动保存", "答案版本单调递增"),
        ("交卷 / 到期", "锁定终态"),
        ("客观题评分", "按确定规则"),
        ("主观题", "人工或后续处理"),
    )
    x = 95
    for index, (title, body) in enumerate(bottom):
        d.box((x, 605, x + 250, 730), title, body, title_size=21, body_size=18, accent=AMBER if index == 4 else SLATE)
        if index < len(bottom) - 1:
            d.connector(((x + 250, 668), (x + 300, 668)), color=SLATE)
        x += 300
    d.note((45, 805, 1555, 885), "当前边界", "旧版本或终态写入被拒绝；客观评分不自动生成画像证据，考试结果尚未回写七维画像。")
    d.save(path)


def _core_entity_relations(path: Path) -> None:
    d = Diagram("赛题主线关键实体逻辑关系", "箭头表示业务逻辑 ID 关联，不等同于数据库已经建立物理外键")
    d.badge((55, 145), "62 个 JPA 实体 / 61 张表")
    d.badge((1260, 145), "52 控制器 / 338 映射", color=SLATE)
    d.box((680, 185, 920, 295), "用户 / 角色", "身份与角色上下文", strong=True, title_size=24, body_size=18)
    d.group((45, 360, 480, 790), "AI 会话与资源", color=TEAL)
    d.box((80, 405, 270, 495), "AI 会话", "用户会话", title_size=21, body_size=17)
    d.box((285, 405, 445, 495), "AI 任务", "路由与终态", title_size=21, body_size=17)
    d.box((80, 590, 270, 680), "资源信封", "来源与完整性", title_size=21, body_size=17)
    d.box((285, 590, 445, 680), "互动 / 导出", "互动与登记", title_size=20, body_size=17)
    d.connector(((270, 440), (285, 440)))
    d.connector(((365, 485), (365, 585)))
    d.connector(((270, 630), (285, 630)))
    d.group((555, 360, 1045, 790), "画像与会议", color=TEAL)
    d.box((590, 405, 785, 495), "画像维度", "七维快照", title_size=21, body_size=17)
    d.box((815, 405, 1010, 495), "画像证据", "候选 / 已应用", title_size=20, body_size=16)
    d.connector(((815, 440), (785, 440)))
    d.box((590, 590, 775, 680), "会议会话", "会议聚合根", title_size=20, body_size=17)
    d.box((805, 565, 1010, 705), "成员 / 记录 / 结果", "参与者 / final / 会后结果", title_size=19, body_size=16)
    d.connector(((775, 630), (805, 630)))
    d.group((1120, 360, 1555, 790), "题库、试卷与考试", color=TEAL)
    d.box((1155, 405, 1340, 495), "题目", "题型结构", title_size=21, body_size=17)
    d.box((1360, 405, 1520, 495), "试卷", "题目集合", title_size=21, body_size=17)
    d.connector(((1340, 440), (1360, 440)))
    d.box((1155, 590, 1340, 680), "预览证明", "签名 / 一次消费", title_size=20, body_size=17)
    d.box((1360, 565, 1520, 705), "考试尝试 / 答案", "快照 / 版本 / 得分", title_size=18, body_size=16)
    d.connector(((1440, 485), (1440, 555)))
    d.connector(((1340, 630), (1360, 630)))
    d.connector(((800, 295), (800, 335), (250, 335), (250, 360)), "用户归属")
    d.connector(((800, 295), (800, 360)), "画像 / 会议归属")
    d.connector(((800, 295), (800, 335), (1340, 335), (1340, 360)), "试卷 / 尝试归属")
    d.note((45, 810, 1555, 885), "约束", "用户 ID、会议 ID、试卷 ID、资源 ID 等字段表达逻辑归属；只有迁移脚本、数据库元数据或映射明确时，才能宣称物理外键。")
    d.save(path)


def _requirements_trace(path: Path) -> None:
    d = Diagram("需求—设计—接口—测试追踪", "11 条测试责任链仅展示可核验 ID；不使用虚构通过率、QPS、准确率或 SLA", height=1300)
    headers = ((55, "需求 ID"), (430, "功能 / 技术设计 ID"), (835, "接口 / 数据 ID"), (1210, "测试追踪 ID"))
    for x, label in headers:
        d.box((x, 165, x + 335, 225), label, "", strong=True, title_size=23)
    rows = (
        ("FR-001/002/024 · NFR-004", "FUNC-01", "API-01 · Role", "TC-01"),
        ("FR-003/004 · NFR-001/002", "FUNC-02 · TECH-02", "API-03 · AI Session/Task", "TC-02"),
        ("FR-005–008 · NFR-003", "FUNC-03 · TECH-01", "API-04/05 · ProfileEvidence", "TC-03"),
        ("FR-015–017 · NFR-003", "FUNC-04 · TECH-06", "ResourceEnvelope · Interaction", "TC-04"),
        ("FR-009–011 · NFR-006", "FUNC-05 · TECH-03/04", "API-06 · MeetingRecord", "TC-05"),
        ("FR-012–014 · NFR-005", "FUNC-06 · TECH-05", "API-07 · KnowledgeChat", "TC-06"),
        ("FR-018/019 · NFR-007", "FUNC-07 · TECH-07", "API-08 · Question JSON", "TC-07"),
        ("FR-020/021 · NFR-003/004", "FUNC-08 · TECH-07", "API-09 · PreviewProof", "TC-08"),
        ("FR-022/023 · NFR-003", "FUNC-09 · TECH-08", "API-10 · AttemptAnswer", "TC-09"),
        ("NFR-002/004/008/009", "FUNC-10 · TECH-03", "Config · WebSocket · Auth", "TC-10"),
        ("NFR-005/006/010", "FUNC-11 · 总体部署", "MySQL/Redis/Java/Python/Web/App", "TC-11"),
    )
    y = 260
    for row in rows:
        for column, value in enumerate(row):
            x = headers[column][0]
            d.box((x, y, x + 335, y + 64), value, "", accent=TEAL, title_size=17)
            if column < 3:
                d.connector(((x + 335, y + 32), (headers[column + 1][0], y + 32)), color=TEAL, width=4)
        y += 76
    d.legend(((TEAL, "implemented"), (SLATE, "partial"), (AMBER, "planned"), (DARK, "known-limit")), y=1125)
    d.note((55, 1175, 1545, 1265), "读取方式", "TC 编号追踪验收责任与证据入口；状态仍以工程证据索引为准，需求目标本身不是实现证明。")
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
