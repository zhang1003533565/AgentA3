import json
from typing import Any, Dict, List


class PptToDocxAgent:
    name = "ppt_to_docx_agent"

    def process(self, input_text: str, evidence: List[Dict[str, Any]], chat_service=None) -> str:
        return json.dumps({
            "agent": self.name,
            "task": "pptx_to_docx",
            "supportedInput": [".pptx"],
            "output": ".docx",
            "conversionEndpoint": "POST /internal/rag/ppt/convert",
            "layoutPolicy": "按幻灯片顺序重排为 Word 文档；不强制保留 PPT 坐标布局。",
            "imagePolicy": "保留 PPTX 中可提取图片，并按原比例限制在 Word 页面宽度内。",
            "usage": "在 RAG 管理页的文档转换区域上传 PPTX 文件，系统会生成可下载 DOCX。",
            "inputMaterial": (input_text or "").strip(),
        }, ensure_ascii=False)


ppt_to_docx_agent = PptToDocxAgent()

__all__ = ["ppt_to_docx_agent"]
