import unittest

from app.services.ppt_template_selection import (
    build_ppt_generation_draft,
    parse_ppt_page_count,
    parse_ppt_template_from_text,
    parse_ppt_topic,
    resolve_ppt_generation_source_content,
    resolve_ppt_template_id,
)


class PptTemplateSelectionTest(unittest.TestCase):
    def test_parse_topic_from_theme_pattern(self):
        text = "请生成一份 8 页 PPT，主题是校园二手交易平台介绍，包含发布商品、沟通议价、线下交易三个部分。"
        self.assertEqual("校园二手交易平台介绍", parse_ppt_topic(text))

    def test_parse_page_count(self):
        self.assertEqual(8, parse_ppt_page_count("请生成一份 8 页 PPT，主题是校园二手交易平台介绍。"))

    def test_parse_template_aliases(self):
        self.assertEqual("dynamic", parse_ppt_template_from_text("用活力校园模板"))
        self.assertEqual("general", parse_ppt_template_from_text("默认模板就好"))
        self.assertIsNone(parse_ppt_template_from_text("校园二手交易平台介绍"))

    def test_resolve_template_requires_confirmation_for_metadata(self):
        self.assertIsNone(resolve_ppt_template_id({"pptSettings": {"templateId": "dynamic"}}, ""))
        self.assertEqual(
            "dynamic",
            resolve_ppt_template_id(
                {"pptSettings": {"templateId": "dynamic"}, "pptTemplateConfirmed": True},
                "",
            ),
        )

    def test_resolve_source_content_keeps_original_prompt_on_template_reply(self):
        draft = build_ppt_generation_draft(
            "请生成一份 8 页 PPT，主题是校园二手交易平台介绍，包含发布商品、沟通议价、线下交易三个部分。"
        )
        resolved = resolve_ppt_generation_source_content(
            "使用活力校园模板",
            {"pptGenerationDraft": draft},
        )
        self.assertIn("校园二手交易平台介绍", resolved)


if __name__ == "__main__":
    unittest.main()
