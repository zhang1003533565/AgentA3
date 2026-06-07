import base64
import io
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, Iterable, List


class PptConversionError(RuntimeError):
    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message)
        self.status_code = status_code


def convert_ppt_to_docx(ppt_bytes: bytes, original_filename: str) -> Dict[str, Any]:
    if not ppt_bytes:
        raise PptConversionError("PPT/PPTX 文件不能为空", 400)
    lower_filename = (original_filename or "").lower()
    if not lower_filename.endswith((".ppt", ".pptx")):
        raise PptConversionError("当前仅支持 .ppt 或 .pptx 文件", 400)

    try:
        from docx import Document
        from docx.shared import Inches
        from pptx import Presentation
        from pptx.enum.shapes import MSO_SHAPE_TYPE
    except Exception as exc:
        raise PptConversionError("PPTX 转 DOCX 依赖未安装，请安装 python-pptx 和 python-docx", 500) from exc

    base_name = _safe_stem(original_filename)
    conversion_mode = "pptx_to_docx_reflow"
    presentation_bytes = ppt_bytes
    if lower_filename.endswith(".ppt"):
        presentation_bytes = _convert_legacy_ppt_to_pptx(ppt_bytes, base_name)
        conversion_mode = "ppt_to_docx_reflow"
    elif not ppt_bytes[:4] == b"PK\x03\x04":
        raise PptConversionError("上传文件不是有效的 PPTX", 400)

    try:
        presentation = Presentation(io.BytesIO(presentation_bytes))
    except Exception as exc:
        raise PptConversionError(f"PPT/PPTX 解析失败：{exc}", 400) from exc

    document = Document()
    document.add_heading(base_name, level=0)
    image_assets: List[Dict[str, Any]] = []

    for slide_index, slide in enumerate(presentation.slides, start=1):
        title = _slide_title(slide) or f"第 {slide_index} 页"
        document.add_heading(f"第 {slide_index} 页：{title}", level=1)
        shapes = sorted(_iter_shapes(slide.shapes), key=lambda shape: (int(getattr(shape, "top", 0) or 0), int(getattr(shape, "left", 0) or 0)))
        wrote_content = False

        for shape in shapes:
            if _is_title_shape(shape, title):
                continue
            if getattr(shape, "has_table", False):
                if _append_table(document, shape):
                    wrote_content = True
                continue
            if getattr(shape, "shape_type", None) == MSO_SHAPE_TYPE.PICTURE:
                asset = _append_picture(document, shape, slide_index, len(image_assets) + 1, Inches)
                if asset:
                    image_assets.append(asset)
                    wrote_content = True
                continue
            if getattr(shape, "has_text_frame", False):
                wrote_content = _append_text_frame(document, shape) or wrote_content

        if not wrote_content:
            document.add_paragraph("本页未检测到可提取的文字、表格或图片。")
        if slide_index < len(presentation.slides):
            document.add_page_break()

    buffer = io.BytesIO()
    document.save(buffer)
    output_bytes = buffer.getvalue()
    return {
        "format": "docx",
        "outputType": "file",
        "downloadType": "file",
        "fileName": f"{base_name}.docx",
        "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "contentBase64": base64.b64encode(output_bytes).decode("ascii"),
        "contentLength": len(output_bytes),
        "assets": image_assets,
        "imageCount": len(image_assets),
        "slideCount": len(presentation.slides),
        "conversionMode": conversion_mode,
    }


def _convert_legacy_ppt_to_pptx(ppt_bytes: bytes, base_name: str) -> bytes:
    soffice = _find_soffice()
    if not soffice:
        raise PptConversionError("当前环境未安装 LibreOffice，无法转换 .ppt；请安装 LibreOffice/soffice 后重试", 500)

    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = Path(temp_dir)
        source_path = temp_path / f"{base_name}.ppt"
        output_path = temp_path / f"{base_name}.pptx"
        source_path.write_bytes(ppt_bytes)
        try:
            result = subprocess.run(
                [
                    soffice,
                    "--headless",
                    "--convert-to",
                    "pptx",
                    "--outdir",
                    str(temp_path),
                    str(source_path),
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=90,
            )
        except subprocess.TimeoutExpired as exc:
            raise PptConversionError(".ppt 转 .pptx 超时，请检查文件大小或 LibreOffice 环境", 500) from exc
        except Exception as exc:
            raise PptConversionError(f".ppt 转 .pptx 失败：{exc}", 500) from exc

        if result.returncode != 0 or not output_path.exists() or output_path.stat().st_size == 0:
            detail = (result.stderr or result.stdout or "").strip()
            suffix = f"：{detail}" if detail else ""
            raise PptConversionError(f".ppt 转 .pptx 失败{suffix}", 500)
        return output_path.read_bytes()


def _find_soffice() -> str:
    for command in ("soffice", "libreoffice"):
        path = shutil.which(command)
        if path:
            return path
    macos_path = "/Applications/LibreOffice.app/Contents/MacOS/soffice"
    if Path(macos_path).exists():
        return macos_path
    return ""


def _iter_shapes(shapes: Iterable[Any]) -> Iterable[Any]:
    for shape in shapes:
        if hasattr(shape, "shapes"):
            yield from _iter_shapes(shape.shapes)
        else:
            yield shape


def _slide_title(slide: Any) -> str:
    title_shape = getattr(slide.shapes, "title", None)
    if title_shape is not None:
        title = _shape_text(title_shape)
        if title:
            return title
    for shape in slide.shapes:
        text = _shape_text(shape)
        if text:
            return text.splitlines()[0].strip()
    return ""


def _is_title_shape(shape: Any, title: str) -> bool:
    if not title:
        return False
    return _normalize_text(_shape_text(shape)) == _normalize_text(title)


def _append_text_frame(document: Any, shape: Any) -> bool:
    wrote = False
    for paragraph in shape.text_frame.paragraphs:
        text = " ".join(run.text for run in paragraph.runs).strip() or (paragraph.text or "").strip()
        if not text:
            continue
        doc_paragraph = document.add_paragraph(text)
        level = int(getattr(paragraph, "level", 0) or 0)
        if level > 0:
            doc_paragraph.paragraph_format.left_indent = _docx_inches(0.22 * min(level, 6))
        wrote = True
    return wrote


def _append_table(document: Any, shape: Any) -> bool:
    table = getattr(shape, "table", None)
    if table is None or not table.rows or not table.columns:
        return False
    doc_table = document.add_table(rows=len(table.rows), cols=len(table.columns))
    doc_table.style = "Table Grid"
    for row_index, row in enumerate(table.rows):
        for col_index, cell in enumerate(row.cells):
            doc_table.cell(row_index, col_index).text = _normalize_text(cell.text)
    return True


def _append_picture(document: Any, shape: Any, slide_index: int, image_index: int, inches_factory: Any) -> Dict[str, Any]:
    image = getattr(shape, "image", None)
    if image is None:
        return {}
    image_bytes = image.blob
    if not image_bytes:
        return {}
    ext = _safe_extension(getattr(image, "ext", "") or "png")
    name = f"slide-{slide_index}-image-{image_index}.{ext}"
    width_inches = max(1.0, min(float(getattr(shape, "width", 0) or 0) / 914400.0, 6.2))
    document.add_picture(io.BytesIO(image_bytes), width=inches_factory(width_inches))
    return {
        "name": name,
        "path": f"assets/{name}",
        "type": "image",
        "mimeType": _image_mime_type(ext),
        "slide": slide_index,
        "size": len(image_bytes),
    }


def _shape_text(shape: Any) -> str:
    if not getattr(shape, "has_text_frame", False):
        return ""
    return _normalize_text(getattr(shape, "text", "") or "")


def _normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def _docx_inches(value: float) -> Any:
    from docx.shared import Inches

    return Inches(value)


def _safe_stem(filename: str) -> str:
    stem = Path(filename or "presentation").stem or "presentation"
    safe = re.sub(r'[\\/:*?"<>|\x00-\x1f]+', "-", stem).strip(".- ")
    return safe or "presentation"


def _safe_extension(ext: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9]+", "", ext.lower())
    return safe or "png"


def _image_mime_type(ext: str) -> str:
    return {
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
        "gif": "image/gif",
        "webp": "image/webp",
        "bmp": "image/bmp",
        "tif": "image/tiff",
        "tiff": "image/tiff",
    }.get(ext, f"image/{ext}")
