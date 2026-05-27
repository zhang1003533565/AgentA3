import base64
import io
import re
import tempfile
import zipfile
from pathlib import Path
from typing import Any, Dict, List


class PdfConversionError(RuntimeError):
    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message)
        self.status_code = status_code


def convert_pdf(pdf_bytes: bytes, original_filename: str, target_format: str) -> Dict[str, Any]:
    fmt = (target_format or "").strip().lower()
    if fmt not in {"md", "docx"}:
        raise PdfConversionError("仅支持转换为 md 或 docx", 400)
    if not pdf_bytes:
        raise PdfConversionError("PDF 文件不能为空", 400)
    if not pdf_bytes.lstrip().startswith(b"%PDF"):
        raise PdfConversionError("上传文件不是有效的 PDF", 400)

    base_name = _safe_stem(original_filename)
    if fmt == "docx":
        return _convert_to_docx(pdf_bytes, base_name)
    return _convert_to_markdown_zip(pdf_bytes, base_name)


def _convert_to_docx(pdf_bytes: bytes, base_name: str) -> Dict[str, Any]:
    try:
        from pdf2docx import Converter
    except Exception as exc:
        raise PdfConversionError("PDF 转 DOCX 依赖未安装，请安装 pdf2docx", 500) from exc

    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = Path(temp_dir)
        source_path = temp_path / f"{base_name}.pdf"
        output_path = temp_path / f"{base_name}.docx"
        source_path.write_bytes(pdf_bytes)

        converter = Converter(str(source_path))
        try:
            converter.convert(str(output_path))
        finally:
            converter.close()

        if not output_path.exists() or output_path.stat().st_size == 0:
            raise PdfConversionError("PDF 转 DOCX 失败：未生成有效文件", 500)

        output_bytes = output_path.read_bytes()
        return {
            "format": "docx",
            "outputType": "file",
            "downloadType": "file",
            "fileName": f"{base_name}.docx",
            "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "contentBase64": base64.b64encode(output_bytes).decode("ascii"),
            "contentLength": len(output_bytes),
            "assets": [],
            "imageCount": None,
        }


def _convert_to_markdown_zip(pdf_bytes: bytes, base_name: str) -> Dict[str, Any]:
    try:
        import fitz
    except Exception as exc:
        raise PdfConversionError("PDF 转 Markdown 依赖未安装，请安装 PyMuPDF", 500) from exc

    assets: List[Dict[str, Any]] = []
    markdown_lines: List[str] = [f"# {base_name}", ""]
    has_extractable_text = False

    try:
        document = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as exc:
        raise PdfConversionError(f"PDF 解析失败：{exc}", 400) from exc

    try:
        for page_index in range(document.page_count):
            page = document.load_page(page_index)
            page_number = page_index + 1
            page_text = (page.get_text("text") or "").strip()
            page_images = []

            if page_text:
                has_extractable_text = True

            for image_index, image in enumerate(page.get_images(full=True), start=1):
                xref = image[0]
                try:
                    image_info = document.extract_image(xref)
                except Exception as exc:
                    raise PdfConversionError(f"第 {page_number} 页图片提取失败：{exc}", 500) from exc
                image_bytes = image_info.get("image") or b""
                if not image_bytes:
                    continue
                ext = _safe_extension(image_info.get("ext") or "png")
                image_name = f"page-{page_number}-image-{image_index}.{ext}"
                relative_path = f"assets/{image_name}"
                page_images.append(relative_path)
                assets.append({
                    "name": image_name,
                    "path": relative_path,
                    "type": "image",
                    "page": page_number,
                    "size": len(image_bytes),
                    "_bytes": image_bytes,
                })

            markdown_lines.extend([f"## 第 {page_number} 页", ""])
            if page_text:
                markdown_lines.extend([page_text, ""])
            for relative_path in page_images:
                markdown_lines.extend([f"![{Path(relative_path).name}]({relative_path})", ""])
    finally:
        document.close()

    if not has_extractable_text:
        raise PdfConversionError("该 PDF 未检测到可提取文本；当前版本不做 OCR，请先对扫描件做 OCR 后再转换", 422)

    markdown = "\n".join(markdown_lines).strip() + "\n"
    zip_buffer = io.BytesIO()
    with zipfile.ZipFile(zip_buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(f"{base_name}.md", markdown)
        for asset in assets:
            archive.writestr(asset["path"], asset["_bytes"])

    public_assets = [
        {key: value for key, value in asset.items() if key != "_bytes"}
        for asset in assets
    ]
    output_bytes = zip_buffer.getvalue()
    return {
        "format": "md",
        "outputType": "markdown",
        "downloadType": "zip",
        "fileName": f"{base_name}-markdown.zip",
        "mimeType": "application/zip",
        "contentBase64": base64.b64encode(output_bytes).decode("ascii"),
        "contentLength": len(output_bytes),
        "preview": markdown[:12000],
        "assets": public_assets,
        "imageCount": len(public_assets),
    }


def _safe_stem(filename: str) -> str:
    stem = Path(filename or "converted").stem or "converted"
    safe = re.sub(r"[^A-Za-z0-9._-]+", "-", stem).strip(".-")
    return safe or "converted"


def _safe_extension(ext: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9]+", "", ext.lower())
    return safe or "png"
