"""Extract uploaded images and compose them into one PNG image."""

import base64
import io
import re
import zipfile
from dataclasses import dataclass
from typing import Any, Iterable, List, Tuple

MAX_STITCH_DIMENSION = 12_000
MAX_STITCH_COLUMNS = 3
MAX_SOURCE_EDGE = 2048
STITCH_OUTPUT_PNG_COMPRESS_LEVEL = 9
SEQUENCE_LABEL_MIN_FONT_SIZE = 18
SEQUENCE_LABEL_MAX_FONT_SIZE = 64
SEQUENCE_LABEL_FILL = (96, 117, 134)
_IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff"}
_DATA_URL_RE = re.compile(r"^data:(?P<mime>[^;,]+);base64,(?P<data>.+)$", re.IGNORECASE | re.DOTALL)


@dataclass(frozen=True)
class StitchImage:
    content: bytes
    name: str
    mime_type: str


class ImageStitchingError(ValueError):
    """Raised when uploaded resources cannot be decoded or composed."""


def collect_stitch_images(request: Any) -> List[StitchImage]:
    """Collect direct images and embedded document images in request order."""
    images: List[StitchImage] = []
    attachments = getattr(request, "attachments", None)
    if isinstance(attachments, (list, tuple)):
        for raw in attachments:
            images.extend(_images_from_attachment(raw))

    # Admin tool tests send imageDataUrls directly instead of attachment objects.
    for attr in ("imageDataUrls", "images", "imageUrls"):
        values = getattr(request, attr, None)
        if isinstance(values, (list, tuple)):
            for index, value in enumerate(values):
                decoded = _decode_value(value)
                if decoded:
                    content, mime_type = decoded
                    images.append(StitchImage(content, f"上传图片-{index + 1}", mime_type))
    return images


def stitch_images(
    images: Iterable[StitchImage],
    *,
    horizontal: bool = False,
    columns: int = MAX_STITCH_COLUMNS,
) -> bytes:
    """Return a PNG containing all images in order, using an adaptive grid of up to three columns."""
    source_images = list(images)
    if len(source_images) < 2:
        raise ImageStitchingError("至少需要两张图片才能进行拼接")

    try:
        return _stitch_with_pillow(source_images, horizontal=horizontal, columns=columns)
    except ImportError:
        pass
    except Exception:
        # Keep the PyMuPDF path as a fallback for formats Pillow does not support.
        pass

    try:
        import fitz
    except ImportError as exc:  # pragma: no cover - dependency is part of the AI server image
        raise ImageStitchingError("图片拼接依赖 PyMuPDF 未安装") from exc

    pixmaps = []
    try:
        for item in source_images:
            try:
                pixmap = _load_pixmap(fitz, item.content)
                if pixmap.alpha or pixmap.n < 3:
                    pixmap = fitz.Pixmap(fitz.csRGB, pixmap)
                elif pixmap.colorspace != fitz.csRGB:
                    pixmap = fitz.Pixmap(fitz.csRGB, pixmap)
                pixmaps.append(pixmap)
            except Exception as exc:
                raise ImageStitchingError(f"无法读取图片：{item.name or '未命名图片'}") from exc

        widths = [pix.width for pix in pixmaps]
        heights = [pix.height for pix in pixmaps]
        gutter_widths = [
            _sequence_gutter_width(width, height, index + 1)
            for index, (width, height) in enumerate(zip(widths, heights))
        ]
        if horizontal:
            group_gutter_widths = gutter_widths
            item_widths = [width + gutter for width, gutter in zip(widths, group_gutter_widths)]
            raw_width = sum(item_widths)
            raw_height = max(heights)
        else:
            grid_columns = max(1, min(MAX_STITCH_COLUMNS, int(columns or MAX_STITCH_COLUMNS), len(pixmaps)))
            column_gutter_widths = [max(gutter_widths[index] for index in range(column, len(gutter_widths), grid_columns))
                                    for column in range(grid_columns)]
            # Use one gutter width per column so all image edges and labels line up.
            group_gutter_widths = [column_gutter_widths[index % grid_columns] for index in range(len(pixmaps))]
            item_widths = [width + gutter for width, gutter in zip(widths, group_gutter_widths)]
            column_widths = [max(item_widths[index] for index in range(column, len(item_widths), grid_columns))
                             for column in range(grid_columns)]
            row_heights = [max(heights[index] for index in range(row * grid_columns,
                                                                min((row + 1) * grid_columns, len(heights))))
                           for row in range((len(pixmaps) + grid_columns - 1) // grid_columns)]
            raw_width = sum(column_widths)
            raw_height = sum(row_heights)
        if raw_width <= 0 or raw_height <= 0:
            raise ImageStitchingError("图片尺寸无效")

        scale = min(1.0, MAX_STITCH_DIMENSION / raw_width, MAX_STITCH_DIMENSION / raw_height)
        canvas_width = max(1, int(round(raw_width * scale)))
        canvas_height = max(1, int(round(raw_height * scale)))

        document = fitz.open()
        try:
            page = document.new_page(width=canvas_width, height=canvas_height)
            if horizontal:
                cursor = 0.0
            else:
                x_offsets = [0]
                for width in column_widths[:-1]:
                    x_offsets.append(x_offsets[-1] + width * scale)
                y_offsets = [0]
                for height in row_heights[:-1]:
                    y_offsets.append(y_offsets[-1] + height * scale)
            for index, pixmap in enumerate(pixmaps):
                width = pixmap.width * scale
                height = pixmap.height * scale
                gutter_width = group_gutter_widths[index] * scale
                item_width = item_widths[index] * scale
                if horizontal:
                    item_left = cursor
                    top = (canvas_height - height) / 2
                    cursor += item_width
                else:
                    column = index % grid_columns
                    row = index // grid_columns
                    cell_height = row_heights[row] * scale
                    # Align each image group to the left edge while centering it vertically.
                    item_left = x_offsets[column]
                    top = y_offsets[row] + (cell_height - height) / 2
                left = item_left + gutter_width
                rect = fitz.Rect(left, top, left + width, top + height)
                page.insert_image(rect, stream=pixmap.tobytes("png"))
                _draw_fitz_sequence_label(
                    page,
                    fitz,
                    index + 1,
                    item_left,
                    top,
                    gutter_width,
                    height,
                )
            rendered = page.get_pixmap(matrix=fitz.Matrix(1, 1), alpha=False)
            return rendered.tobytes("png")
        finally:
            document.close()
    finally:
        for pixmap in pixmaps:
            try:
                pixmap = None
            except Exception:
                pass


def _stitch_with_pillow(
    images: List[StitchImage],
    *,
    horizontal: bool,
    columns: int,
) -> bytes:
    from PIL import Image, ImageDraw, ImageFont, ImageOps

    normalized_images = []
    for item in images:
        with Image.open(io.BytesIO(item.content)) as source:
            normalized_images.append(
                _resize_if_needed(ImageOps.exif_transpose(source).convert("RGB"))
            )

    widths = [image.width for image in normalized_images]
    heights = [image.height for image in normalized_images]
    gutter_widths = [
        _sequence_gutter_width(width, height, index + 1)
        for index, (width, height) in enumerate(zip(widths, heights))
    ]
    if horizontal:
        group_gutter_widths = gutter_widths
        grid_columns = 0
        decoded = [
            _add_pillow_sequence_gutter(Image, ImageDraw, ImageFont, image, index + 1, gutter_width=gutter_width)
            for index, (image, gutter_width) in enumerate(zip(normalized_images, group_gutter_widths))
        ]
        widths = [image.width for image in decoded]
        heights = [image.height for image in decoded]
        raw_width = sum(widths)
        raw_height = max(heights)
        column_widths = []
        row_heights = []
    else:
        grid_columns = max(1, min(MAX_STITCH_COLUMNS, int(columns or MAX_STITCH_COLUMNS), len(normalized_images)))
        column_gutter_widths = [max(gutter_widths[index] for index in range(column, len(gutter_widths), grid_columns))
                                for column in range(grid_columns)]
        # Use one gutter width per column so all image edges and labels line up.
        group_gutter_widths = [column_gutter_widths[index % grid_columns] for index in range(len(normalized_images))]
        decoded = [
            _add_pillow_sequence_gutter(Image, ImageDraw, ImageFont, image, index + 1, gutter_width=gutter_width)
            for index, (image, gutter_width) in enumerate(zip(normalized_images, group_gutter_widths))
        ]
        widths = [image.width for image in decoded]
        heights = [image.height for image in decoded]
        column_widths = [max(widths[index] for index in range(column, len(widths), grid_columns))
                         for column in range(grid_columns)]
        row_heights = [max(heights[index] for index in range(row * grid_columns,
                                                            min((row + 1) * grid_columns, len(heights))))
                       for row in range((len(decoded) + grid_columns - 1) // grid_columns)]
        raw_width = sum(column_widths)
        raw_height = sum(row_heights)
    if raw_width <= 0 or raw_height <= 0:
        raise ImageStitchingError("图片尺寸无效")

    scale = min(1.0, MAX_STITCH_DIMENSION / raw_width, MAX_STITCH_DIMENSION / raw_height)
    canvas_width = max(1, int(round(raw_width * scale)))
    canvas_height = max(1, int(round(raw_height * scale)))
    canvas = Image.new("RGB", (canvas_width, canvas_height), "white")
    cursor = 0
    x_offsets = [0]
    y_offsets = [0]
    if not horizontal:
        for width in column_widths[:-1]:
            x_offsets.append(x_offsets[-1] + int(round(width * scale)))
        for height in row_heights[:-1]:
            y_offsets.append(y_offsets[-1] + int(round(height * scale)))
    for index, image in enumerate(decoded):
        width = max(1, int(round(image.width * scale)))
        height = max(1, int(round(image.height * scale)))
        if width != image.width or height != image.height:
            image = image.resize((width, height), Image.Resampling.LANCZOS)
        if horizontal:
            position = (cursor, (canvas_height - height) // 2)
            cursor += width
        else:
            column = index % grid_columns
            row = index // grid_columns
            cell_height = max(1, int(round(row_heights[row] * scale)))
            position = (
                x_offsets[column],
                y_offsets[row] + (cell_height - height) // 2,
            )
        canvas.paste(image, position)

    output = io.BytesIO()
    canvas.save(output, format="PNG", optimize=True, compress_level=STITCH_OUTPUT_PNG_COMPRESS_LEVEL)
    return output.getvalue()


def _resize_if_needed(image: Any, max_edge: int = MAX_SOURCE_EDGE) -> Any:
    from PIL import Image as PilImage

    width, height = image.size
    longest = max(width, height)
    if longest <= max_edge:
        return image
    scale = max_edge / longest
    return image.resize(
        (max(1, int(round(width * scale))), max(1, int(round(height * scale)))),
        PilImage.Resampling.LANCZOS,
    )


def _sequence_label_metrics(width: float, height: float) -> Tuple[int, int]:
    shortest_edge = max(1.0, min(width, height))
    margin = max(2, int(round(shortest_edge * 0.025)))
    font_size = max(
        SEQUENCE_LABEL_MIN_FONT_SIZE,
        min(SEQUENCE_LABEL_MAX_FONT_SIZE, int(round(shortest_edge * 0.13))),
    )
    return margin, font_size


def _sequence_gutter_width(width: float, height: float, sequence: int) -> int:
    margin, font_size = _sequence_label_metrics(width, height)
    digit_width = font_size * 0.7 * len(str(sequence))
    return max(font_size + margin * 2, int(round(digit_width + font_size * 0.8 + margin * 2)))


def _add_pillow_sequence_gutter(
    image_module: Any,
    image_draw: Any,
    image_font: Any,
    image: Any,
    sequence: int,
    gutter_width: int | None = None,
) -> Any:
    margin, font_size = _sequence_label_metrics(image.width, image.height)
    try:
        font = image_font.truetype("DejaVuSans-Bold.ttf", font_size)
    except (OSError, ValueError):
        try:
            font = image_font.load_default(size=font_size)
        except TypeError:  # Pillow before scalable default fonts
            font = image_font.load_default()

    gutter_width = gutter_width or _sequence_gutter_width(image.width, image.height, sequence)
    item = image_module.new("RGB", (gutter_width + image.width, image.height), "white")
    item.paste(image, (gutter_width, 0))
    draw = image_draw.Draw(item)
    label = str(sequence)
    bounds = draw.textbbox((0, 0), label, font=font)
    text_width = max(1, bounds[2] - bounds[0])
    text_height = max(1, bounds[3] - bounds[1])
    padding_x = max(4, int(round(font_size * 0.3)))
    padding_y = max(3, int(round(font_size * 0.2)))
    left = max(margin, (gutter_width - text_width - padding_x * 2) // 2)
    top = max(0, (image.height - text_height - padding_y * 2) // 2)
    right = left + text_width + padding_x * 2
    bottom = top + text_height + padding_y * 2
    draw.rounded_rectangle(
        (left, top, right, bottom),
        radius=max(3, int(round(font_size * 0.25))),
        fill=SEQUENCE_LABEL_FILL,
    )
    draw.text(
        (left + padding_x - bounds[0], top + padding_y - bounds[1]),
        label,
        font=font,
        fill=(255, 255, 255),
    )
    return item


def _draw_fitz_sequence_label(
    page: Any,
    fitz: Any,
    sequence: int,
    gutter_left: float,
    top: float,
    gutter_width: float,
    height: float,
) -> None:
    _, font_size = _sequence_label_metrics(gutter_width, height)
    # Keep the fallback label inside its scaled gutter for very wide images.
    font_size = max(3.0, min(float(font_size), gutter_width * 0.35, height * 0.6))
    label = str(sequence)
    padding_x = max(1.0, min(4.0, font_size * 0.3))
    padding_y = max(1.0, min(3.0, font_size * 0.2))
    label_width = max(font_size * 0.65 * len(label), font_size * 0.6) + padding_x * 2
    label_height = font_size + padding_y * 2
    label_left = gutter_left + max(0.0, (gutter_width - label_width) / 2)
    label_top = top + max(0.0, (height - label_height) / 2)
    page.draw_rect(
        fitz.Rect(label_left, label_top, label_left + label_width, label_top + label_height),
        color=tuple(channel / 255 for channel in SEQUENCE_LABEL_FILL),
        fill=tuple(channel / 255 for channel in SEQUENCE_LABEL_FILL),
        overlay=True,
    )
    page.insert_text(
        (label_left + padding_x, label_top + padding_y + font_size * 0.82),
        label,
        fontsize=font_size,
        fontname="helv",
        color=(1, 1, 1),
        overlay=True,
    )


def _load_pixmap(fitz: Any, content: bytes) -> Any:
    """Decode browser uploads, normalizing formats PyMuPDF cannot open directly."""
    try:
        return fitz.Pixmap(stream=content)
    except Exception as primary_error:
        try:
            from PIL import Image, ImageOps

            with Image.open(io.BytesIO(content)) as source:
                normalized = ImageOps.exif_transpose(source).convert("RGB")
                buffer = io.BytesIO()
                normalized.save(buffer, format="PNG")
            return fitz.Pixmap(stream=buffer.getvalue())
        except Exception:
            try:
                import cv2
                import numpy as np

                decoded = cv2.imdecode(np.frombuffer(content, dtype=np.uint8), cv2.IMREAD_UNCHANGED)
                if decoded is None:
                    raise ValueError("OpenCV 无法识别图片格式")
                encoded_ok, encoded = cv2.imencode(".png", decoded)
                if not encoded_ok:
                    raise ValueError("OpenCV 图片转码失败")
                return fitz.Pixmap(stream=encoded.tobytes())
            except Exception:
                raise primary_error


def _images_from_attachment(raw: Any) -> List[StitchImage]:
    if not isinstance(raw, dict):
        return []
    name = str(raw.get("name") or raw.get("fileName") or "上传文件").strip()
    mime_type = str(raw.get("mimeType") or raw.get("contentType") or "").strip().lower()
    values = [raw.get("contentBase64"), raw.get("dataUrl"), raw.get("dataURL"), raw.get("previewDataUrl")]
    decoded = next((item for value in values if (item := _decode_value(value))), None)
    if decoded:
        content, decoded_mime = decoded
        effective_mime = mime_type or decoded_mime
        if _is_image(name, effective_mime):
            return [StitchImage(content, name, effective_mime)]
        return _extract_embedded_images(content, name, effective_mime)

    for key in ("url", "fileUrl", "href"):
        decoded = _decode_value(raw.get(key))
        if decoded:
            content, decoded_mime = decoded
            if _is_image(name, mime_type or decoded_mime):
                return [StitchImage(content, name, mime_type or decoded_mime)]
    return []


def _decode_value(value: Any) -> Tuple[bytes, str] | None:
    if not isinstance(value, str) or not value.strip():
        return None
    text = value.strip()
    mime_type = ""
    match = _DATA_URL_RE.match(text)
    if match:
        mime_type = match.group("mime").lower()
        text = match.group("data")
    try:
        return base64.b64decode(text, validate=True), mime_type
    except (ValueError, TypeError):
        return None


def _is_image(name: str, mime_type: str) -> bool:
    if str(mime_type or "").lower().startswith("image/"):
        return True
    extension = str(name or "").rsplit(".", 1)[-1].lower() if "." in str(name or "") else ""
    return extension in _IMAGE_EXTENSIONS


def _extract_embedded_images(content: bytes, name: str, mime_type: str) -> List[StitchImage]:
    extension = str(name or "").rsplit(".", 1)[-1].lower() if "." in str(name or "") else ""
    normalized_mime = str(mime_type or "").lower()
    if extension == "pdf" or normalized_mime == "application/pdf":
        return _extract_pdf_images(content, name)
    if extension in {"pptx", "docx", "xlsx", "zip"} or normalized_mime in {
        "application/zip",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }:
        return _extract_zip_images(content, name)
    return []


def _extract_pdf_images(content: bytes, name: str) -> List[StitchImage]:
    try:
        import fitz

        document = fitz.open(stream=content, filetype="pdf")
        extracted: List[StitchImage] = []
        try:
            for page_index, page in enumerate(document):
                for image_index, image_info in enumerate(page.get_images(full=True)):
                    extracted_data = document.extract_image(image_info[0])
                    image_bytes = extracted_data.get("image") if isinstance(extracted_data, dict) else None
                    if image_bytes:
                        extension = str(extracted_data.get("ext") or "png")
                        extracted.append(StitchImage(
                            image_bytes,
                            f"{name}-第{page_index + 1}页-{image_index + 1}.{extension}",
                            f"image/{extension}",
                        ))
        finally:
            document.close()
        return extracted
    except Exception as exc:
        raise ImageStitchingError(f"无法读取 PDF 中的图片：{name}") from exc


def _extract_zip_images(content: bytes, name: str) -> List[StitchImage]:
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            extracted: List[StitchImage] = []
            for info in archive.infolist():
                member_name = info.filename
                extension = member_name.rsplit(".", 1)[-1].lower() if "." in member_name else ""
                if extension not in _IMAGE_EXTENSIONS or member_name.endswith("/"):
                    continue
                image_bytes = archive.read(info)
                extracted.append(StitchImage(image_bytes, f"{name}/{member_name}", f"image/{extension}"))
            return extracted
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        raise ImageStitchingError(f"无法读取文件中的图片：{name}") from exc
