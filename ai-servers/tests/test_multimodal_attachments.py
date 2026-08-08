import unittest

from app.model_providers.multimodal import append_attachment_references_to_text


class MultimodalAttachmentTests(unittest.TestCase):
    def test_non_image_resources_are_appended_to_the_prompt(self):
        prompt = append_attachment_references_to_text(
            "总结这些资料",
            [
                {"name": "课程笔记.pdf", "type": "document", "url": "https://files.test/notes.pdf"},
                {"name": "封面.png", "type": "image", "url": "https://files.test/cover.png"},
            ],
        )

        self.assertIn("课程笔记.pdf", prompt)
        self.assertIn("https://files.test/notes.pdf", prompt)
        self.assertNotIn("cover.png", prompt)

    def test_duplicate_resource_urls_are_only_added_once(self):
        prompt = append_attachment_references_to_text(
            "对比",
            [
                {"name": "A.csv", "url": "https://files.test/data.csv"},
                {"name": "B.csv", "url": "https://files.test/data.csv"},
            ],
        )

        self.assertEqual(1, prompt.count("https://files.test/data.csv"))


if __name__ == "__main__":
    unittest.main()
