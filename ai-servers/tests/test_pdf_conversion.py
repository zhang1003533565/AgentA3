import base64
import io
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import fitz
from docx import Document

from app.rag.document_conversion import PdfConversionError, convert_pdf


ONE_PIXEL_PNG = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
)


class PdfConversionTest(unittest.TestCase):
    def _sample_pdf(self) -> bytes:
        document = fitz.open()
        page = document.new_page(width=300, height=220)
        page.insert_text((36, 60), "Smart Campus PDF Convert Test", fontsize=14)
        page.insert_text((36, 90), "This page has extractable text.", fontsize=11)
        page.insert_image(
            fitz.Rect(36, 110, 86, 160),
            stream=base64.b64decode(ONE_PIXEL_PNG),
        )
        data = document.tobytes()
        document.close()
        return data

    def test_pdf_to_markdown_zip_keeps_image_assets(self):
        result = convert_pdf(self._sample_pdf(), "sample.pdf", "md")
        self.assertEqual("md", result["format"])
        self.assertEqual("zip", result["downloadType"])
        self.assertEqual(1, result["imageCount"])
        self.assertIn("Smart Campus PDF Convert Test", result["preview"])
        self.assertTrue(result["assets"][0]["previewDataUrl"].startswith("data:image/png;base64,"))

        with TemporaryDirectory() as temp_dir:
            zip_path = Path(temp_dir) / "sample.zip"
            zip_path.write_bytes(base64.b64decode(result["contentBase64"]))
            with zipfile.ZipFile(zip_path) as archive:
                self.assertIn("sample.md", archive.namelist())
                self.assertIn("assets/page-1-image-1.png", archive.namelist())

    def test_pdf_to_docx_generates_downloadable_file(self):
        result = convert_pdf(self._sample_pdf(), "sample.pdf", "docx")
        self.assertEqual("docx", result["format"])
        self.assertEqual("file", result["downloadType"])
        self.assertTrue(result["fileName"].endswith(".docx"))
        self.assertGreater(result["contentLength"], 0)
        self.assertEqual("pdf_to_docx_reflow", result["conversionMode"])
        self.assertEqual(1, result["imageCount"])

        docx_bytes = base64.b64decode(result["contentBase64"])
        document = Document(io.BytesIO(docx_bytes))
        text = "\n".join(paragraph.text for paragraph in document.paragraphs)
        self.assertIn("Smart Campus PDF Convert Test", text)
        self.assertIn("This page has extractable text.", text)

        with zipfile.ZipFile(io.BytesIO(docx_bytes)) as archive:
            media_files = [name for name in archive.namelist() if name.startswith("word/media/")]
        self.assertTrue(media_files)

    def test_pdf_to_docx_rejects_scanned_pdf(self):
        document = fitz.open()
        page = document.new_page(width=300, height=220)
        page.insert_image(
            fitz.Rect(0, 0, 300, 220),
            stream=base64.b64decode(ONE_PIXEL_PNG),
        )
        scanned_pdf = document.tobytes()
        document.close()

        with self.assertRaises(PdfConversionError) as ctx:
            convert_pdf(scanned_pdf, "scan.pdf", "docx")
        self.assertEqual(422, ctx.exception.status_code)

    def test_invalid_pdf_fails_without_fallback(self):
        with self.assertRaises(PdfConversionError) as ctx:
            convert_pdf(b"not a pdf", "sample.pdf", "md")
        self.assertEqual(400, ctx.exception.status_code)


if __name__ == "__main__":
    unittest.main()
