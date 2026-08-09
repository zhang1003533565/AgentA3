"""AI 架构图生成服务。

负责：
1. 构造 Prompt（要求大模型严格返回 JSON）
2. 调用大模型
3. 解析 JSON（兼容 ```json 代码块包裹）
4. 校验节点关系（边必须引用存在的节点 id）
5. 返回结构化结果

与现有 diagram_architecture_agent（返回 Mermaid 文本）独立，本服务输出用户要求的
{ title, style, nodes, edges } JSON 结构，供前端按节点/连线渲染。
"""

from __future__ import annotations

import json
import re
from typing import Any, Dict, List, Optional

from fastapi import HTTPException

from app.model_providers.factory import get_chat_model_provider
from app.model_providers.runtime_config import set_active_llm_config, reset_active_llm_config, build_llm_runtime_config


SYSTEM_PROMPT = """你是一名资深系统架构师，负责根据用户需求生成结构完整的企业级软件系统架构图。

【核心要求】必须输出完整的 6 层分层结构 + 右侧第三方服务 + 底部特性，缺一不可。

【6 个标准架构层】（从上到下，必须全部输出，即使该层没有内容也要输出空 nodes 数组）
1. 客户端层（client）：用户直接接触的入口。常见节点：移动 App、Web 端、微信小程序、管理后台、PC 客户端、桌面端等。
2. 接入层（gateway）：请求入口和流量网关。常见节点：Nginx、Spring Cloud Gateway、API Gateway、SLB、CDN、负载均衡等。
3. 服务层（service）：业务微服务。常见节点：用户服务、商品服务、订单服务、消息服务、搜索服务、文件服务、支付服务等。每个服务都要有 technologies（如 ["Spring Boot"]）和 description。
4. 数据访问层（dao）：ORM 框架。常见节点：MyBatis-Plus、MyBatis、Hibernate、JPA 等。
5. 数据存储层（storage）：数据库和存储。常见节点：MySQL、PostgreSQL、Redis、MongoDB、Elasticsearch、ClickHouse 等。
6. 基础设施层（infra）：部署和运维。常见节点：Docker、Kubernetes、Linux、CI/CD、Jenkins、Prometheus、Grafana、ELK、日志中心、监控告警 等。

【第三方服务】（thirdParty 数组，必填，至少 3 个）
- 短信服务、对象存储 OSS、邮件服务、支付服务（微信/支付宝）、第三方登录、消息推送、地图服务、身份认证等。

【底部特性标签】（features 数组，必填，至少 4 个）
- 从以下候选选取：高可用、易扩展、高性能、安全可靠、可维护、高并发、低延迟、易部署、容错性、自动化 等。
- 必须与架构实际特性匹配。

【返回格式】严格 JSON，**只输出 JSON**，不要任何解释、Markdown 代码块标记或额外文本。

JSON 结构：
{
  "title": "架构标题",
  "style": "架构风格描述，如：前后端分离 / 微服务",
  "subtitle": "副标题，3-5 个关键词用 · 分隔",
  "layers": [
    {
      "key": "client",
      "name": "客户端层",
      "color": "#4D6BFE",
      "bg": "#EEF0FF",
      "border": "#C7D2FE",
      "iconKey": "monitor",
      "nodes": [
        {
          "name": "移动 App",
          "description": "买卖物品、下单交易",
          "tech": ["Vue3", "Vite"]
        },
        {
          "name": "Web 端",
          "description": "浏览商品、管理订单",
          "tech": ["Vue3"]
        },
        {
          "name": "微信小程序",
          "description": "快速交易、消息通知",
          "tech": ["UniApp"]
        },
        {
          "name": "管理后台",
          "description": "运营管理、数据统计",
          "tech": ["Vue3", "Element"]
        }
      ]
    },
    {
      "key": "gateway",
      "name": "接入层",
      "color": "#8B5CF6",
      "bg": "#F5F3FF",
      "border": "#DDD6FE",
      "iconKey": "nginx",
      "nodes": [
        {
          "name": "Nginx",
          "description": "静态资源、反向代理、HTTPS、负载均衡",
          "tech": ["Nginx"]
        },
        {
          "name": "Spring Cloud Gateway",
          "description": "API 路由、鉴权、限流、熔断降级",
          "tech": ["Spring Cloud Gateway"]
        }
      ]
    },
    {
      "key": "service",
      "name": "服务层",
      "color": "#10B981",
      "bg": "#ECFDF5",
      "border": "#A7F3D0",
      "iconKey": "shop",
      "nodes": [
        {
          "name": "用户服务",
          "description": "用户管理、认证授权、个人信息",
          "tech": ["Spring Boot", "JWT"]
        }
      ]
    },
    {
      "key": "dao",
      "name": "数据访问层",
      "color": "#3B82F6",
      "bg": "#EFF6FF",
      "border": "#BFDBFE",
      "iconKey": "database",
      "nodes": [
        {
          "name": "MyBatis-Plus",
          "description": "ORM 框架、SQL 映射、事务管理、分页插件",
          "tech": ["MyBatis-Plus"]
        }
      ]
    },
    {
      "key": "storage",
      "name": "数据存储层",
      "color": "#EC4899",
      "bg": "#FDF2F8",
      "border": "#FBCFE8",
      "iconKey": "database",
      "nodes": [
        {
          "name": "MySQL",
          "description": "业务数据存储、用户、商品、订单等",
          "tech": ["MySQL 8.0"]
        }
      ]
    },
    {
      "key": "infra",
      "name": "基础设施层",
      "color": "#F59E0B",
      "bg": "#FFFBEB",
      "border": "#FDE68A",
      "iconKey": "server",
      "nodes": [
        {
          "name": "Docker",
          "description": "容器化部署、环境隔离",
          "tech": ["Docker"]
        }
      ]
    }
  ],
  "thirdParty": [
    {
      "name": "短信服务",
      "description": "验证码、通知",
      "iconKey": "sms"
    },
    {
      "name": "对象存储",
      "description": "图片、文件存储",
      "iconKey": "oss"
    },
    {
      "name": "支付服务",
      "description": "微信支付、支付宝",
      "iconKey": "payment"
    }
  ],
  "requestedRelationMode": "AUTO",
  "resolvedRelationMode": "MODULE",
  "nodes": [
    {
      "id": "user_service",
      "name": "用户服务",
      "type": "service",
      "layer": "service",
      "description": "用户管理、认证授权"
    }
  ],
  "edges": [
    {
      "source": "gateway",
      "target": "user_service",
      "type": "structural",
      "label": "结构连接",
      "direction": "none"
    }
  ],
  "features": ["高可用", "易扩展", "高性能", "安全可靠", "可维护"]
}

【字段约束】
- title: 字符串，不能为空
- subtitle: 字符串，3-5 个关键词用 · 分隔
- style: 字符串，架构风格描述
- layers: 必须是 6 个，顺序固定 client → gateway → service → dao → storage → infra
- layers[].key: 固定为 client/gateway/service/dao/storage/infra 之一
- layers[].name: 中文层名
- layers[].color/bg/border/iconKey: 按上面示例填写
- layers[].nodes: 每层 1-6 个节点
- layers[].nodes[].name: 节点名（如 "用户服务"）
- layers[].nodes[].description: 1-2 行功能描述
- layers[].nodes[].tech: 技术栈数组，至少 1 个
- thirdParty: 数组，至少 3 个元素；每项含 name/description/iconKey
- features: 数组，至少 4 个中文特性词
- requestedRelationMode: 用户请求的关系表达，取 AUTO/MODULE/DATA_FLOW/CALL
- resolvedRelationMode: 最终采用的关系表达；当 requestedRelationMode=AUTO 时必须在 MODULE/DATA_FLOW/CALL 中选择一个
- nodes: 从 layers 中展开得到的节点数组，每个节点必须有稳定 id/layer/type/name/description
- edges: 关系数组，每条边必须有 source/target/type/label/direction

【关键规则】
- 客户端层至少 3 个节点（移动 App / Web 端 / 微信小程序 / 管理后台 等）
- 服务层至少 4 个节点（用户/商品/订单/消息/搜索/文件 等）
- 数据存储层至少 2 个节点（MySQL + Redis + Elasticsearch 等组合）
- 基础设施层至少 3 个节点（Docker / Linux / CI/CD / 监控告警 / 日志中心 等）
- 第三方服务至少 3 个（短信/对象存储/支付/邮件 等）
- 特性至少 4 个
- MODULE 模式：edges[].type 使用 structural，连接线强调层级和模块归属，direction 可为 none/forward，不要强行把所有结构线做成数据流
- DATA_FLOW 模式：edges[].type 使用 dataFlow，必须突出数据从入口到服务再到存储的方向，direction 使用 forward，label 可写请求、用户数据、订单数据等
- CALL 模式：edges[].type 使用 call，必须突出服务或模块之间谁调用谁，direction 使用 forward，label 可写调用、API、服务依赖等；不要把它画成数据最终入库路径
- AUTO 模式：先分析需求，再把 resolvedRelationMode 设置为 MODULE/DATA_FLOW/CALL 中最合适的一个，并按该模式生成 edges
- **必须输出 layers + thirdParty + features + nodes + edges + requestedRelationMode + resolvedRelationMode**
"""


def _normalize_relation_mode(value: str) -> str:
    text = (value or "AUTO").strip().upper()
    if text in {"DATA", "DATAFLOW"}:
        return "DATA_FLOW"
    if text in {"CALL_CHAIN", "CALLING", "DEPENDENCY"}:
        return "CALL"
    if text in {"AUTO", "MODULE", "DATA_FLOW", "CALL"}:
        return text
    return "AUTO"


def _resolve_auto_relation_mode(description: str, requested_relation_mode: str) -> str:
    requested = _normalize_relation_mode(requested_relation_mode)
    if requested != "AUTO":
        return requested
    text = (description or "").lower()
    data_keywords = ["数据流", "流向", "存储路径", "入库", "缓存", "同步", "传递", "data flow", "pipeline"]
    call_keywords = ["调用", "依赖", "接口", "api", "rpc", "rest", "service", "服务间", "链路"]
    if any(keyword in text for keyword in data_keywords):
        return "DATA_FLOW"
    if any(keyword in text for keyword in call_keywords):
        return "CALL"
    return "MODULE"


def _relation_instruction(requested_relation_mode: str, resolved_relation_mode: str) -> str:
    if requested_relation_mode == "AUTO":
        prefix = f"关系表达：AUTO。请先判断最适合的表达方式，当前初步判断为 {resolved_relation_mode}，如需求明显更适合其他模式，可在 resolvedRelationMode 中改为 MODULE/DATA_FLOW/CALL。"
    else:
        prefix = f"关系表达：{requested_relation_mode}。resolvedRelationMode 必须使用 {resolved_relation_mode}。"
    mode_text = {
        "MODULE": "MODULE 要突出系统组成、层级、模块归属与结构连接，edge.type 使用 structural。",
        "DATA_FLOW": "DATA_FLOW 要突出数据从来源到服务再到存储的方向路径，edge.type 使用 dataFlow，direction 使用 forward。",
        "CALL": "CALL 要突出模块或服务之间谁调用谁，edge.type 使用 call，direction 使用 forward。",
    }.get(resolved_relation_mode, "按需求选择合适关系表达。")
    return f"{prefix}\n{mode_text}"


def _layer_strength_instruction(auto_architecture_layers: bool, layers: List[str]) -> str:
    normalized_layers = [str(layer).strip().upper() for layer in layers if str(layer).strip()]
    if auto_architecture_layers or not normalized_layers:
        return "架构层级：AUTO。由 AI 根据需求决定各层内容密度，但仍必须输出标准 6 层结构。"
    return (
        "架构层级：用户已手动选择 "
        + ", ".join(normalized_layers)
        + "。这是强包含约束：所选层级必须出现并重点展开；未选层级可作为必要支撑保留，但不能抢主视觉。"
    )


def _focus_strength_instruction(display_content: List[str]) -> str:
    normalized = [str(item).strip().upper() for item in display_content if str(item).strip()]
    if not normalized:
        return "重点展示：AUTO。由 AI 根据需求自动判断重点模块。"
    return (
        "重点展示："
        + ", ".join(normalized)
        + "。这是强引导，不是排他约束：生成完整架构时必须优先展开这些内容，但不能删除必要的上下游模块。"
    )


def _system_type_instruction(system_type: str) -> str:
    normalized = (system_type or "WEB").strip().upper()
    if normalized == "AUTO":
        return "系统类型：AUTO。由 AI 根据需求判断系统形态。"
    return f"系统类型：{normalized}。这是强约束：入口节点、模块命名和架构边界必须符合该系统形态。"


def _slugify(value: str, fallback: str) -> str:
    text = re.sub(r"[^0-9A-Za-z_\u4e00-\u9fff]+", "_", value or "").strip("_")
    return text or fallback


def _flatten_layer_nodes(layers: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    nodes: List[Dict[str, Any]] = []
    used_ids = set()
    for layer in layers:
        layer_key = str(layer.get("key") or "layer")
        for index, node in enumerate(layer.get("nodes") or []):
            name = str(node.get("name") or "").strip()
            if not name:
                continue
            node_id = str(node.get("id") or _slugify(name, f"{layer_key}_{index + 1}"))
            if node_id in used_ids:
                node_id = f"{node_id}_{index + 1}"
            used_ids.add(node_id)
            nodes.append({
                "id": node_id,
                "name": name,
                "type": str(node.get("type") or layer_key),
                "layer": layer_key,
                "description": str(node.get("description") or ""),
            })
    return nodes


def _edge_type_for_mode(resolved_relation_mode: str) -> str:
    if resolved_relation_mode == "DATA_FLOW":
        return "dataFlow"
    if resolved_relation_mode == "CALL":
        return "call"
    return "structural"


def _normalize_edges(raw_edges: Any, nodes: List[Dict[str, Any]], resolved_relation_mode: str) -> List[Dict[str, Any]]:
    node_ids = {node["id"] for node in nodes}
    edge_type = _edge_type_for_mode(resolved_relation_mode)
    direction = "none" if resolved_relation_mode == "MODULE" else "forward"
    edges: List[Dict[str, Any]] = []
    if isinstance(raw_edges, list):
        for edge in raw_edges:
            if not isinstance(edge, dict):
                continue
            source = str(edge.get("source") or edge.get("from") or "").strip()
            target = str(edge.get("target") or edge.get("to") or "").strip()
            if source not in node_ids or target not in node_ids or source == target:
                continue
            edges.append({
                "source": source,
                "target": target,
                "type": edge_type,
                "label": str(edge.get("label") or ""),
                "direction": direction,
            })
    if edges:
        return edges
    return _build_default_edges(nodes, resolved_relation_mode)


def _layer_has_nodes(layers: List[Dict[str, Any]], layer_keys: List[str]) -> bool:
    wanted = set(layer_keys)
    for layer in layers:
        if layer.get("key") in wanted and layer.get("nodes"):
            return True
    return False


def _validate_selected_layers(layers: List[Dict[str, Any]], selected_layers: List[str]) -> None:
    layer_requirements = {
        "CLIENT": ["client"],
        "APPLICATION": ["gateway", "service"],
        "SERVICE": ["service"],
        "DATA": ["dao", "storage"],
    }
    missing: List[str] = []
    for raw in selected_layers or []:
        key = str(raw or "").strip().upper()
        required_keys = layer_requirements.get(key)
        if required_keys and not _layer_has_nodes(layers, required_keys):
            missing.append(key)
    if missing:
        raise HTTPException(status_code=502, detail=f"架构图缺少用户强制选择的层级：{', '.join(missing)}")


def _validate_focus_contents(layers: List[Dict[str, Any]], third_party: List[Dict[str, Any]], focus_contents: List[str]) -> None:
    focus_requirements = {
        "FRONTEND": ["client"],
        "BACKEND": ["service"],
        "DATABASE": ["storage"],
    }
    missing: List[str] = []
    for raw in focus_contents or []:
        key = str(raw or "").strip().upper()
        if key == "THIRD_PARTY":
            if not third_party:
                missing.append(key)
            continue
        required_keys = focus_requirements.get(key)
        if required_keys and not _layer_has_nodes(layers, required_keys):
            missing.append(key)
    if missing:
        raise HTTPException(status_code=502, detail=f"架构图缺少重点展示内容：{', '.join(missing)}")


def _build_default_edges(nodes: List[Dict[str, Any]], resolved_relation_mode: str) -> List[Dict[str, Any]]:
    by_layer: Dict[str, List[Dict[str, Any]]] = {}
    for node in nodes:
        by_layer.setdefault(node.get("layer") or "", []).append(node)
    layer_order = ["client", "gateway", "service", "dao", "storage", "infra"]
    edge_type = _edge_type_for_mode(resolved_relation_mode)
    direction = "none" if resolved_relation_mode == "MODULE" else "forward"
    label_map = {
        "MODULE": "结构连接",
        "DATA_FLOW": "数据传递",
        "CALL": "调用",
    }
    edges: List[Dict[str, Any]] = []
    for left_key, right_key in zip(layer_order, layer_order[1:]):
        left_nodes = by_layer.get(left_key) or []
        right_nodes = by_layer.get(right_key) or []
        if not left_nodes or not right_nodes:
            continue
        if resolved_relation_mode == "MODULE":
            pairs = [(left_nodes[0], right_nodes[0])]
        else:
            pairs = [(source, right_nodes[index % len(right_nodes)]) for index, source in enumerate(left_nodes[:3])]
        for source, target in pairs:
            edges.append({
                "source": source["id"],
                "target": target["id"],
                "type": edge_type,
                "label": label_map.get(resolved_relation_mode, "连接"),
                "direction": direction,
            })
    return edges


def _build_user_prompt(
    description: str,
    system_type: str,
    architecture_style: str,
    layers: List[str],
    display_content: List[str],
    requested_relation_mode: str,
    resolved_relation_mode: str,
    auto_architecture_layers: bool,
) -> str:
    """把用户输入和配置参数组装成 user prompt。"""
    parts: List[str] = []
    parts.append(f"需求描述：{description or '(未提供)'}")
    if architecture_style:
        parts.append(f"架构模式：{architecture_style}")
    parts.append("规则强度说明：")
    parts.append(_system_type_instruction(system_type))
    parts.append(_layer_strength_instruction(auto_architecture_layers, layers))
    parts.append(_focus_strength_instruction(display_content))
    parts.append(_relation_instruction(requested_relation_mode, resolved_relation_mode))
    parts.append("请基于上述需求生成架构图 JSON。")
    return "\n".join(parts)


_JSON_BLOCK_RE = re.compile(r"```(?:json)?\s*([\s\S]*?)```", re.IGNORECASE)


def _extract_json(raw: str) -> str:
    """从大模型返回中提取 JSON 字符串，兼容裸 JSON 和 ```json 代码块。"""
    text = (raw or "").strip()
    if not text:
        raise HTTPException(status_code=502, detail="架构图 LLM 返回内容为空")
    # 优先匹配 ```json ... ``` 代码块
    match = _JSON_BLOCK_RE.search(text)
    if match:
        candidate = match.group(1).strip()
        if candidate:
            return candidate
    # 没有代码块则尝试直接作为 JSON 解析
    return text


def _parse_architecture(
    raw: str,
    requested_relation_mode: str,
    resolved_relation_mode: str,
    system_type: str,
    auto_architecture_layers: bool,
    architecture_layers: List[str],
    focus_contents: List[str],
) -> Dict[str, Any]:
    """解析并校验架构 JSON。"""
    json_str = _extract_json(raw)
    try:
        data = json.loads(json_str)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"架构图 LLM 返回的不是合法 JSON：{exc.msg}",
        ) from exc

    if not isinstance(data, dict):
        raise HTTPException(status_code=502, detail="架构图 JSON 顶层必须是对象")

    title = data.get("title")
    if not isinstance(title, str) or not title.strip():
        raise HTTPException(status_code=502, detail="架构图 JSON 缺少 title 字段")

    style = data.get("style", "")
    if not isinstance(style, str):
        style = str(style)
    subtitle = data.get("subtitle", "")
    if not isinstance(subtitle, str):
        subtitle = str(subtitle)

    # 解析 layers（必须 6 个标准层）
    raw_layers = data.get("layers")
    if not isinstance(raw_layers, list) or not raw_layers:
        # 兜底：如果 LLM 仍返回 nodes + edges，按 type 归类
        nodes = data.get("nodes")
        edges = data.get("edges")
        if isinstance(nodes, list) and nodes:
            return _parse_legacy_nodes(
                nodes,
                edges,
                title,
                style,
                subtitle,
                requested_relation_mode,
                resolved_relation_mode,
                system_type,
                auto_architecture_layers,
                architecture_layers,
                focus_contents,
            )
        raise HTTPException(status_code=502, detail="架构图 JSON 缺少 layers 数组")

    # 标准 6 层定义
    STANDARD_LAYERS = [
        {"key": "client",  "name": "客户端层",   "color": "#4D6BFE", "bg": "#EEF0FF", "border": "#C7D2FE", "iconKey": "monitor"},
        {"key": "gateway", "name": "接入层",     "color": "#8B5CF6", "bg": "#F5F3FF", "border": "#DDD6FE", "iconKey": "nginx"},
        {"key": "service", "name": "服务层",     "color": "#10B981", "bg": "#ECFDF5", "border": "#A7F3D0", "iconKey": "shop"},
        {"key": "dao",     "name": "数据访问层", "color": "#3B82F6", "bg": "#EFF6FF", "border": "#BFDBFE", "iconKey": "database"},
        {"key": "storage", "name": "数据存储层", "color": "#EC4899", "bg": "#FDF2F8", "border": "#FBCFE8", "iconKey": "database"},
        {"key": "infra",   "name": "基础设施层", "color": "#F59E0B", "bg": "#FFFBEB", "border": "#FDE68A", "iconKey": "server"},
    ]
    std_map = {s["key"]: s for s in STANDARD_LAYERS}

    # 解析原始 layers，索引到标准 key
    provided = {}
    for layer in raw_layers:
        if not isinstance(layer, dict):
            continue
        key = str(layer.get("key") or "").strip()
        if not key:
            continue
        provided[key] = layer

    # 输出 6 层（缺失的层用空 nodes 兜底）
    normalized_layers = []
    for std in STANDARD_LAYERS:
        key = std["key"]
        if key in provided:
            pl = provided[key]
            nodes_raw = pl.get("nodes")
            if not isinstance(nodes_raw, list):
                nodes_raw = []
            nodes = []
            for n in nodes_raw:
                if not isinstance(n, dict):
                    continue
                name = str(n.get("name") or "").strip()
                if not name:
                    continue
                desc = str(n.get("description") or "").strip()
                tech_raw = n.get("tech") or n.get("technologies") or []
                if not isinstance(tech_raw, list):
                    tech_raw = []
                tech = [str(t).strip() for t in tech_raw if str(t).strip()]
                nodes.append({
                    "name": name,
                    "description": desc,
                    "tech": tech,
                    "iconKey": n.get("iconKey") or std["iconKey"],
                })
            normalized_layers.append({
                **std,
                "name": str(pl.get("name") or std["name"]).strip(),
                "nodes": nodes,
            })
        else:
            # 该层缺失，输出空 nodes
            normalized_layers.append({**std, "nodes": []})

    # 解析 thirdParty
    raw_tp = data.get("thirdParty") or data.get("third_party") or []
    third_party = []
    if isinstance(raw_tp, list):
        for tp in raw_tp:
            if not isinstance(tp, dict):
                continue
            name = str(tp.get("name") or "").strip()
            if not name:
                continue
            third_party.append({
                "name": name,
                "description": str(tp.get("description") or "").strip(),
                "iconKey": str(tp.get("iconKey") or "sms").strip() or "sms",
            })
    if not third_party:
        # 兜底：给一个默认第三方服务
        third_party = [
            {"name": "短信服务", "description": "验证码、通知", "iconKey": "sms"},
            {"name": "对象存储", "description": "图片、文件存储", "iconKey": "oss"},
            {"name": "支付服务", "description": "微信支付、支付宝", "iconKey": "payment"},
        ]

    # 解析 features
    raw_features = data.get("features") or []
    features = []
    if isinstance(raw_features, list):
        for f in raw_features:
            f_str = str(f).strip()
            if f_str:
                features.append(f_str)
    if not features:
        features = ["高可用", "易扩展", "高性能", "安全可靠", "可维护"]

    if requested_relation_mode == "AUTO":
        model_resolved_mode = _normalize_relation_mode(
            data.get("resolvedRelationMode") or data.get("relationMode") or resolved_relation_mode
        )
        final_relation_mode = model_resolved_mode if model_resolved_mode != "AUTO" else resolved_relation_mode
    else:
        final_relation_mode = resolved_relation_mode
    nodes = _flatten_layer_nodes(normalized_layers)
    _validate_selected_layers(normalized_layers, architecture_layers)
    _validate_focus_contents(normalized_layers, third_party, focus_contents)
    edges = _normalize_edges(data.get("edges"), nodes, final_relation_mode)

    return {
        "title": title.strip(),
        "style": style.strip(),
        "subtitle": subtitle.strip() or "分层解耦 · 高可用 · 易扩展",
        "layers": normalized_layers,
        "thirdParty": third_party,
        "features": features,
        "nodes": nodes,
        "edges": edges,
        "systemType": system_type or "WEB",
        "autoArchitectureLayers": auto_architecture_layers,
        "architectureLayers": architecture_layers,
        "focusContents": focus_contents,
        "requestedRelationMode": requested_relation_mode,
        "resolvedRelationMode": final_relation_mode,
        "relationMode": final_relation_mode,
    }


def _parse_legacy_nodes(
    nodes,
    edges,
    title,
    style,
    subtitle,
    requested_relation_mode,
    resolved_relation_mode,
    system_type,
    auto_architecture_layers,
    architecture_layers,
    focus_contents,
):
    """兜底解析：LLM 仍返回扁平 nodes + edges 时使用。"""
    # 按 type 归类到 6 个标准层
    LAYER_TYPES = {
        "client":  ["frontend", "client", "web", "app", "mini_program"],
        "gateway": ["gateway", "nginx", "lb", "load_balancer"],
        "service": ["service", "business"],
        "dao":     ["orm", "dao"],
        "storage": ["database", "cache", "search", "message_queue", "queue"],
        "infra":   ["infrastructure", "monitor", "log", "devops", "third_party"],
    }
    STANDARD_LAYERS = [
        {"key": "client",  "name": "客户端层",   "color": "#4D6BFE", "bg": "#EEF0FF", "border": "#C7D2FE", "iconKey": "monitor"},
        {"key": "gateway", "name": "接入层",     "color": "#8B5CF6", "bg": "#F5F3FF", "border": "#DDD6FE", "iconKey": "nginx"},
        {"key": "service", "name": "服务层",     "color": "#10B981", "bg": "#ECFDF5", "border": "#A7F3D0", "iconKey": "shop"},
        {"key": "dao",     "name": "数据访问层", "color": "#3B82F6", "bg": "#EFF6FF", "border": "#BFDBFE", "iconKey": "database"},
        {"key": "storage", "name": "数据存储层", "color": "#EC4899", "bg": "#FDF2F8", "border": "#FBCFE8", "iconKey": "database"},
        {"key": "infra",   "name": "基础设施层", "color": "#F59E0B", "bg": "#FFFBEB", "border": "#FDE68A", "iconKey": "server"},
    ]
    used = set()
    layers = []
    for std in STANDARD_LAYERS:
        types = LAYER_TYPES.get(std["key"], [])
        matched = []
        for n in nodes:
            if not isinstance(n, dict) or n.get("id") in used:
                continue
            t = (n.get("type") or "").lower()
            if t in types:
                used.add(n.get("id"))
                matched.append({
                    "name": n.get("name") or "",
                    "description": n.get("description") or "",
                    "tech": n.get("technologies") or n.get("tech") or [],
                    "iconKey": n.get("iconKey") or std["iconKey"],
                })
        layers.append({**std, "nodes": matched})
    _validate_selected_layers(layers, architecture_layers)
    third_party = [
        {"name": "短信服务", "description": "验证码、通知", "iconKey": "sms"},
        {"name": "对象存储", "description": "图片、文件存储", "iconKey": "oss"},
        {"name": "支付服务", "description": "微信支付、支付宝", "iconKey": "payment"},
    ]
    _validate_focus_contents(layers, third_party, focus_contents)
    flat_nodes = _flatten_layer_nodes(layers)
    normalized_edges = _normalize_edges(edges, flat_nodes, resolved_relation_mode)
    return {
        "title": title.strip() if isinstance(title, str) else "AI 架构图",
        "style": style.strip() if isinstance(style, str) else "",
        "subtitle": subtitle.strip() if isinstance(subtitle, str) else "分层解耦 · 高可用 · 易扩展",
        "layers": layers,
        "thirdParty": third_party,
        "features": ["高可用", "易扩展", "高性能", "安全可靠", "可维护"],
        "nodes": flat_nodes,
        "edges": normalized_edges,
        "systemType": system_type or "WEB",
        "autoArchitectureLayers": auto_architecture_layers,
        "architectureLayers": architecture_layers,
        "focusContents": focus_contents,
        "requestedRelationMode": requested_relation_mode,
        "resolvedRelationMode": resolved_relation_mode,
        "relationMode": resolved_relation_mode,
    }


class ArchitectureAIService:
    """架构图 AI 生成服务。"""

    def generate(
        self,
        description: str,
        system_type: str = "",
        architecture_style: str = "",
        layers: Optional[List[str]] = None,
        display_content: Optional[List[str]] = None,
        relation_type: str = "",
        auto_architecture_layers: bool = True,
        llm_headers: Optional[Dict[str, str]] = None,
    ) -> Dict[str, Any]:
        """调用大模型生成架构图 JSON。

        Args:
            description: 用户输入的系统需求描述
            system_type: 系统类型（WEB/APP/MINI_PROGRAM/ADMIN/MICROSERVICE/IOT/AI）
            architecture_style: 架构模式（AUTO/MONOLITH/FRONT_BACKEND_SEPARATION/MICROSERVICE/CLOUD_NATIVE）
            layers: 架构层级数组（ACCESS/APPLICATION/SERVICE/DATA/INFRASTRUCTURE）
            display_content: 展示内容数组（FRONTEND/BACKEND/DATABASE/CACHE/MESSAGE_QUEUE/THIRD_PARTY/DEPLOYMENT）
            relation_type: 关系表达（AUTO/MODULE/DATA_FLOW/CALL）
            auto_architecture_layers: 是否由 AI 自动分析架构层级
            llm_headers: 由 Java 后端透传的 X-AI-* 头，用于配置 LLM provider

        Returns:
            { title, style, nodes, edges }
        """
        requested_relation_mode = _normalize_relation_mode(relation_type)
        resolved_relation_mode = _resolve_auto_relation_mode(description, requested_relation_mode)
        user_prompt = _build_user_prompt(
            description=description,
            system_type=system_type,
            architecture_style=architecture_style,
            layers=layers or [],
            display_content=display_content or [],
            requested_relation_mode=requested_relation_mode,
            resolved_relation_mode=resolved_relation_mode,
            auto_architecture_layers=auto_architecture_layers,
        )

        # 将 Java 透传的 LLM 配置写入线程上下文
        token = None
        if llm_headers:
            token = set_active_llm_config(build_llm_runtime_config(
                provider=llm_headers.get("provider"),
                base_url=llm_headers.get("base_url"),
                api_key=llm_headers.get("api_key"),
                model=llm_headers.get("model"),
            ))
        try:
            provider = get_chat_model_provider()
            raw_answer = provider.complete(
                system_prompt=SYSTEM_PROMPT,
                user_prompt=user_prompt,
            )
        finally:
            if token is not None:
                reset_active_llm_config(token)

        return _parse_architecture(
            raw_answer,
            requested_relation_mode=requested_relation_mode,
            resolved_relation_mode=resolved_relation_mode,
            system_type=system_type,
            auto_architecture_layers=auto_architecture_layers,
            architecture_layers=layers or [],
            focus_contents=display_content or [],
        )


architecture_ai_service = ArchitectureAIService()
