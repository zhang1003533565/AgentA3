"""Single source of truth for uploaded, detected, and generated file formats."""

from copy import deepcopy
from typing import Any, Dict, List


# 维护文件格式时只需要修改这里。运行时校验、AI 链接识别和后台展示都会读取这张表。
FILE_FORMAT_REGISTRY: List[Dict[str, Any]] = [
    {
        "key": "image",
        "name": "图片",
        "type": "image",
        "extensions": ["png", "jpg", "jpeg", "gif", "webp", "bmp"],
        "mimeTypes": ["image/*"],
        "canUpload": True,
        "canDetect": True,
        "canExport": False,
        "tool": "recognize_image_tool",
        "description": "图片理解、OCR、截图和图表分析。",
    },
    {
        "key": "pdf",
        "name": "PDF 文档",
        "type": "pdf",
        "extensions": ["pdf"],
        "mimeTypes": ["application/pdf"],
        "canUpload": True,
        "canDetect": True,
        "canExport": False,
        "tool": "generated_export_tools",
        "description": "PDF 文档输入和链接识别。",
    },
    {
        "key": "video",
        "name": "视频",
        "type": "video",
        "extensions": ["mp4", "mov", "m4v", "webm", "ogg"],
        "mimeTypes": ["video/*"],
        "canUpload": True,
        "canDetect": True,
        "canExport": False,
        "tool": "",
        "description": "视频输入和链接识别。",
    },
    {
        "key": "docx",
        "name": "Word 文档",
        "type": "docx",
        "extensions": ["doc", "docx"],
        "mimeTypes": ["application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"],
        "canUpload": True,
        "canDetect": True,
        "canExport": True,
        "tool": "docx_export_tool",
        "description": "Word 文档输入、链接识别和导出。",
    },
    {
        "key": "pptx",
        "name": "PPT 演示文稿",
        "type": "ppt",
        "extensions": ["ppt", "pptx"],
        "mimeTypes": ["application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"],
        "canUpload": True,
        "canDetect": True,
        "canExport": True,
        "tool": "pptx_export_tool",
        "description": "PPT 演示文稿输入、链接识别和导出。",
    },
    {
        "key": "spreadsheet",
        "name": "Excel 表格",
        "type": "excel",
        "extensions": ["xls", "xlsx", "csv"],
        "mimeTypes": ["application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv"],
        "canUpload": True,
        "canDetect": True,
        "canExport": True,
        "tool": "excel_export_tool",
        "description": "Excel/CSV 表格输入、链接识别和导出。",
    },
    {
        "key": "markdown",
        "name": "Markdown 文件",
        "type": "md",
        "extensions": ["md", "mmd"],
        "mimeTypes": ["text/markdown", "text/plain"],
        "canUpload": True,
        "canDetect": True,
        "canExport": True,
        "tool": "markdown_export_tool",
        "description": "Markdown 和 Mermaid 源文件输入、链接识别和导出。",
    },
    {
        "key": "txt",
        "name": "纯文本文件",
        "type": "txt",
        "extensions": ["txt"],
        "mimeTypes": ["text/plain"],
        "canUpload": True,
        "canDetect": True,
        "canExport": True,
        "tool": "text_to_txt_tool",
        "description": "纯文本文件输入和按原文导出。",
    },
    {
        "key": "archive",
        "name": "压缩包",
        "type": "zip",
        "extensions": ["zip"],
        "mimeTypes": ["application/zip"],
        "canUpload": True,
        "canDetect": True,
        "canExport": True,
        "tool": "content_archive_tool",
        "description": "附件压缩包输入、链接识别和导出。",
    },
]


def get_file_format_registry() -> List[Dict[str, Any]]:
    return deepcopy(FILE_FORMAT_REGISTRY)


def get_detectable_extensions() -> List[str]:
    return sorted({ext.lower().lstrip(".") for item in FILE_FORMAT_REGISTRY if item.get("canDetect") for ext in item.get("extensions", [])})


def get_upload_extensions() -> List[str]:
    return sorted({ext.lower().lstrip(".") for item in FILE_FORMAT_REGISTRY if item.get("canUpload") for ext in item.get("extensions", [])})


def get_output_aliases() -> set[str]:
    aliases = {"document", "file"}
    for item in FILE_FORMAT_REGISTRY:
        if item.get("canExport"):
            aliases.add(str(item.get("type") or item.get("key") or "").lower())
            aliases.update(str(ext).lower().lstrip(".") for ext in item.get("extensions", []))
    aliases.update({"word", "excel", "markdown"})
    return aliases


def resolve_file_format(extension: str, type_hint: str = "") -> Dict[str, Any]:
    ext = str(extension or "").lower().lstrip(".")
    hint = str(type_hint or "").lower()
    for item in FILE_FORMAT_REGISTRY:
        if ext in {str(value).lower().lstrip(".") for value in item.get("extensions", [])}:
            return item
        if hint and (hint == str(item.get("type") or "").lower() or hint == str(item.get("key") or "").lower()):
            return item
    return {}
