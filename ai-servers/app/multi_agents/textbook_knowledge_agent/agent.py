import json
from typing import Any, Dict, List

from app.multi_agents.runtime import complete_agent_or_raise
from app.services.data_store import data_store


MODEL_GENERATION_REQUEST_TOKENS = (
    "自己生成", "自行生成", "直接生成", "你来生成", "帮我生成", "由你生成",
    "模型生成", "根据主题生成", "生成知识点", "生成知识材料", "没有材料",
    "没有素材", "不上传材料", "不用材料", "无需材料",
)
MODEL_GENERATED_NOTICE = "> 来源说明：以下知识材料由模型根据用户主题生成，未使用上传材料或知识库证据；用于正式题库前请先确认内容。"


def resolve_knowledge_source_mode(topic: str, evidence: List[Dict[str, Any]]) -> str:
    normalized = str(topic or "").strip().lower()
    has_material_evidence = any(
        isinstance(item, dict)
        and str(item.get("source") or "").strip().lower() not in {"", "user_profile", "profile_context"}
        and str(item.get("content") or item.get("text") or "").strip()
        for item in (evidence or [])
    )
    if has_material_evidence:
        return "provided_material"
    if any(token in normalized for token in MODEL_GENERATION_REQUEST_TOKENS):
        return "model_generated"
    return "source_selection_required"


class TextbookKnowledgeAgent:
    def retrieve(self, authorization: str, intent: str, keyword: str, input_text: str) -> List[Dict[str, Any]]:
        results, _ = self.retrieve_with_meta(authorization, intent, keyword, input_text)
        return results

    def retrieve_with_meta(
        self,
        authorization: str,
        intent: str,
        keyword: str,
        input_text: str,
    ) -> tuple[List[Dict[str, Any]], Dict[str, Any]]:
        if not keyword and intent != "schedule":
            return [], self._meta(0)
        if intent == "schedule":
            results = data_store.search_schedule(authorization, input_text)
        else:
            results = data_store.search_keyword(authorization, keyword)
        return results, self._meta(len(results))

    def summarize_knowledge_points(self, topic: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        source_mode = resolve_knowledge_source_mode(topic, evidence)
        request_payload = json.dumps({
            "userRequest": topic,
            "knowledgeSourceMode": source_mode,
            "sourcePolicy": (
                "严格依据 evidence 整理，不补写材料之外的事实。"
                if source_mode == "provided_material"
                else (
                    "用户已明确授权无材料生成；依据主题生成结构化知识材料，并标记为模型生成内容。"
                    if source_mode == "model_generated"
                    else "当前没有材料且用户尚未选择来源；只询问用户要上传/选择材料，还是授权模型按主题自行生成，不要直接生成知识材料。"
                )
            ),
        }, ensure_ascii=False)
        answer = complete_agent_or_raise(
            "textbook_knowledge_agent",
            request_payload,
            evidence,
            model_provider=chat_service,
        )
        if source_mode == "model_generated" and MODEL_GENERATED_NOTICE not in answer:
            raise RuntimeError("教材知识点模型未按约定标注模型生成来源，已拒绝返回未标注内容")
        return answer

    def _meta(self, java_backend_count: int) -> Dict[str, Any]:
        return {
            "javaBackendCount": java_backend_count,
            "documentCount": 0,
            "localKnowledgeBase": False,
            "localRagStrategies": False,
        }


textbook_knowledge_agent = TextbookKnowledgeAgent()


__all__ = [
    "MODEL_GENERATED_NOTICE",
    "TextbookKnowledgeAgent",
    "resolve_knowledge_source_mode",
    "textbook_knowledge_agent",
]
