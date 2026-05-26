import tempfile
import unittest
from pathlib import Path

from app.rag.indexing.document_loader import DocumentLoader
from app.rag.indexing.multimodal_parser import MultimodalParser


class MultimodalParserTest(unittest.TestCase):
    def test_parse_tsv_table(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "table.tsv"
            path.write_text("name\tprice\n黄焖鸡\t18\n", encoding="utf-8")

            parsed = MultimodalParser().parse(str(path))

            self.assertEqual("table", parsed["modality"])
            self.assertIn("黄焖鸡 | 18", parsed["text"])

    def test_parse_image_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "notice.png"
            path.write_bytes(b"fake-image")

            parsed = MultimodalParser().parse(str(path))

            self.assertEqual("image", parsed["modality"])
            self.assertIn("notice.png", parsed["text"])

    def test_document_loader_supports_pdf_suffix(self):
        self.assertIn(".pdf", DocumentLoader.SUPPORTED_SUFFIXES)


if __name__ == "__main__":
    unittest.main()
