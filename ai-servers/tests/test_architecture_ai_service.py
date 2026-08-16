import json

from app.services.architecture_ai_service import _parse_architecture


def test_parse_architecture_preserves_groups_children_and_child_edges():
    raw = json.dumps({
        "title": "校园交易系统架构图",
        "style": "前后端分离",
        "subtitle": "分层解耦 · 模块清晰 · 可扩展",
        "requestedHierarchyMode": "STRUCTURED",
        "resolvedHierarchyMode": "STRUCTURED",
        "layers": [
            {
                "key": "client",
                "name": "客户端层",
                "groups": [
                    {
                        "id": "client_entry",
                        "name": "用户入口",
                        "description": "多端访问",
                        "nodes": [
                            {
                                "id": "mobile_app",
                                "name": "移动 App",
                                "description": "移动端交易入口",
                                "tech": ["UniApp"],
                                "children": [
                                    {
                                        "id": "publish_page",
                                        "name": "发布页面",
                                        "description": "商品发布表单",
                                        "tech": ["Vue3"],
                                    }
                                ],
                            }
                        ],
                    }
                ],
            },
            {
                "key": "gateway",
                "name": "接入层",
                "nodes": [
                    {
                        "id": "api_gateway",
                        "name": "API 网关",
                        "description": "路由与鉴权",
                        "tech": ["Gateway"],
                    }
                ],
            },
            {
                "key": "service",
                "name": "服务层",
                "groups": [
                    {
                        "id": "trade_services",
                        "name": "交易服务组",
                        "nodes": [
                            {
                                "id": "product_service",
                                "name": "商品服务",
                                "description": "发布与浏览",
                                "tech": ["Spring Boot"],
                                "children": [
                                    {
                                        "id": "publish_module",
                                        "name": "发布模块",
                                        "description": "发布校验",
                                        "tech": ["MyBatis"],
                                    }
                                ],
                            }
                        ],
                    }
                ],
            },
            {"key": "dao", "name": "数据访问层", "nodes": [{"id": "mybatis", "name": "MyBatis", "tech": ["MyBatis"]}]},
            {"key": "storage", "name": "数据存储层", "nodes": [{"id": "mysql", "name": "MySQL", "tech": ["MySQL"]}]},
            {"key": "infra", "name": "基础设施层", "nodes": [{"id": "docker", "name": "Docker", "tech": ["Docker"]}]},
        ],
        "thirdParty": [{"name": "对象存储", "description": "图片文件", "iconKey": "oss"}],
        "features": ["可维护", "易扩展", "安全可靠", "高可用"],
        "requestedRelationMode": "CALL",
        "resolvedRelationMode": "CALL",
        "edges": [
            {"source": "publish_page", "target": "api_gateway", "label": "提交"},
            {"source": "api_gateway", "target": "publish_module", "label": "调用"},
        ],
    }, ensure_ascii=False)

    result = _parse_architecture(
        raw,
        requested_relation_mode="CALL",
        resolved_relation_mode="CALL",
        system_type="WEB",
        auto_architecture_layers=True,
        architecture_layers=[],
        focus_contents=[],
    )

    client = result["layers"][0]
    service = result["layers"][2]
    assert client["groups"][0]["nodes"][0]["children"][0]["id"] == "publish_page"
    assert service["groups"][0]["nodes"][0]["children"][0]["parentId"] == "product_service"
    assert service["groups"][0]["nodes"][0]["children"][0]["level"] == 2

    flattened = {node["id"]: node for node in result["nodes"]}
    assert "publish_page" in flattened
    assert flattened["publish_page"]["parentId"] == "mobile_app"
    assert flattened["publish_module"]["groupId"] == "trade_services"

    assert result["resolvedHierarchyMode"] == "STRUCTURED"
    assert result["edges"] == [
        {
            "source": "publish_page",
            "target": "api_gateway",
            "type": "call",
            "label": "提交",
            "direction": "forward",
        },
        {
            "source": "api_gateway",
            "target": "publish_module",
            "type": "call",
            "label": "调用",
            "direction": "forward",
        },
    ]
