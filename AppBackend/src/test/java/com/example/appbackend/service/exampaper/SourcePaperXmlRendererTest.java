package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourcePaperXmlRendererTest {

    private final SourcePaperXmlRenderer renderer = new SourcePaperXmlRenderer();

    @Test
    void rendersSourceScoreAndSectionTablesWithExactGeometryAndBorders() throws Exception {
        String score = renderer.renderScoreTable(3);
        parse(score);
        assertEquals(8, occurrences(score, "w:tcW w:w=\"1000\""));
        assertTrue(score.contains("w:trHeight w:val=\"520\" w:hRule=\"atLeast\""));
        assertEquals(6, occurrences(score, "w:val=\"single\" w:sz=\"4\" w:color=\"000000\""));
        assertTrue(score.contains(">题号<"));
        assertTrue(score.contains(">一<"));
        assertTrue(score.contains(">三<"));

        String section = renderer.renderSectionHeader("二、简答题(共2题, 共20分)");
        parse(section);
        assertTrue(section.contains("w:tcW w:w=\"2164\""));
        assertTrue(section.contains("w:tcW w:w=\"3040\""));
        assertEquals(4, occurrences(section, "w:tcW w:w=\"969\""));
        assertEquals(2, occurrences(section, "w:trHeight w:val=\"549\" w:hRule=\"atLeast\""));
        assertTrue(section.contains("<w:top w:val=\"none\"/>"));
        assertTrue(section.contains(">阅卷人<"));
        assertTrue(section.contains(">得分<"));
    }

    @Test
    void rendersInlineOptionsOnlyUnderExactSourceThreshold() throws Exception {
        PaperVO paper = paper(List.of(
                question(1, 1, "single_choice", "短选项", options("甲", "十四个字以内"), "{\"correctOption\":\"A\"}"),
                question(2, 1, "single_choice", "长选项", options("123456789012345", "乙"), "{\"correctOption\":\"B\"}"),
                question(3, 1, "single_choice", "五选项", options("一", "二", "三", "四", "五"), "{\"correctOption\":\"E\"}")
        ));

        String xml = renderer.renderQuestions(paper, layout());
        parse(xml);

        assertTrue(xml.contains("A. 甲        B. 十四个字以内"));
        assertFalse(xml.contains("A. 123456789012345        B. 乙"));
        assertFalse(xml.contains("A. 一        B. 二        C. 三        D. 四        E. 五"));
        assertEquals(8, occurrences(xml, "w:ind w:left=\"420\""));
    }

    @Test
    void labelsOptionsByArrayPositionAndMapsOriginalAnswerKeysToThoseLabels() throws Exception {
        PaperVO paper = paper(List.of(
                question(1, 1, "single_choice", "乱序键", "{\"options\":[{\"key\":\"X\",\"text\":\"甲\"},{\"key\":\"Q\",\"text\":\"乙\"}]}", "{\"correctOption\":\"Q\"}"),
                question(2, 2, "multiple_choice", "非标准键", "{\"options\":[{\"key\":\"left\",\"text\":\"丙\"},{\"key\":\"right\",\"text\":\"丁\"}]}", "{\"correctOptions\":[\"right\",\"left\"]}")
        ));

        String questions = renderer.renderQuestions(paper, layout());
        assertTrue(questions.contains("A. 甲        B. 乙"));
        assertTrue(questions.contains("A. 丙        B. 丁"));
        assertFalse(questions.contains("X. 甲"));

        String answers = renderer.renderAnswers(paper, layout());
        assertTrue(answers.contains("1．答案:B"));
        assertTrue(answers.contains("2．答案:B,A"));
    }

    @Test
    void preservesCompleteStructuredBodiesForComplexSubjectiveTypes() throws Exception {
        PaperVO paper = paper(List.of(
                question(1, 1, "material_analysis", "阅读材料", "{\"material\":\"背景材料\",\"subQuestions\":[{\"stem\":\"子问题一\",\"requirements\":[\"要点甲\",\"要点乙\"]}]}", "{}"),
                question(2, 2, "programming", "编写程序", "{\"description\":\"求和\",\"inputFormat\":\"两个整数\",\"outputFormat\":\"一个整数\",\"examples\":[{\"input\":\"1 2\",\"output\":\"3\"}]}", "{}")
        ));

        String xml = renderer.renderQuestions(paper, layout());
        String text = parse(xml).getDocumentElement().getTextContent();
        for (String expected : List.of("背景材料", "subQuestions", "子问题一", "要点甲", "要点乙",
                "求和", "两个整数", "一个整数", "examples", "1 2", "3")) {
            assertTrue(text.contains(expected), expected + " must be retained in rendered body");
        }
        assertTrue(xml.contains("[简答题]阅读材料"));
        assertTrue(xml.contains("[简答题]编写程序"));
    }

    @Test
    void rendersEssayBlankLinesChineseSectionsAndAnswerPageBreak() throws Exception {
        PaperVO paper = paper(List.of(
                question(1, 1, "single_choice", "选择", options("甲", "乙"), "{\"correctOption\":\"A\"}"),
                question(2, 2, "short_answer", "说明原因", "{}", "{\"referenceAnswer\":\"参考内容\"}")
        ));
        paper.getQuestions().get(1).setAnalysis("解析内容");

        String questions = renderer.renderQuestions(paper, layout());
        parse(questions);
        assertTrue(questions.contains("一、单项选择题(共1题, 共5分)"));
        assertTrue(questions.contains("二、简答题(共1题, 共5分)"));
        assertTrue(questions.contains("2．[简答题]说明原因(5分)"));
        PaperVO essayOnly = paper(List.of(question(1, 1, "short_answer", "说明原因", "{}", "{}")));
        String essayXml = renderer.renderQuestions(essayOnly, layout());
        assertEquals(13, occurrences(essayXml, "<w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr></w:p>"),
                "源码为 12 个答题空行，随后还有 1 个分区间距段落");

        String answers = renderer.renderAnswers(paper, layout());
        parse(answers);
        assertTrue(answers.startsWith("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"));
        assertTrue(answers.contains("答案解析"));
        assertTrue(answers.contains("1．答案:A"));
        assertTrue(answers.contains("2．答案:参考内容"));
        assertTrue(answers.contains("解析:解析内容"));
    }

    @Test
    void cleansHtmlDecodesEntitiesAndEscapesAllDynamicXml() throws Exception {
        PaperVO paper = paper(List.of(question(1, 1, "custom", "<b>A&amp;B</b> &lt;x&gt; &#39;引号&#39;", "not<json & raw", "{bad<&")));

        String questions = renderer.renderQuestions(paper, layout());
        Document questionDocument = parse(questions);
        String questionText = questionDocument.getDocumentElement().getTextContent();
        assertTrue(questionText.contains("A&B <x> '引号'"));
        assertTrue(questionText.contains("not<json & raw"));
        assertFalse(questions.contains("<b>"));
        assertTrue(questions.contains("A&amp;B &lt;x&gt; &apos;引号&apos;"));
        assertTrue(questions.contains("not&lt;json &amp; raw"));

        String answers = renderer.renderAnswers(paper, layout());
        assertEquals("{bad<&", parse(answers).getDocumentElement().getTextContent().substring(
                parse(answers).getDocumentElement().getTextContent().indexOf("{bad<&"),
                parse(answers).getDocumentElement().getTextContent().indexOf("{bad<&") + 6));
        assertTrue(answers.contains("{bad&lt;&amp;"));
    }

    @Test
    void decodesHugeNumericEntitiesWithoutOverflowLikeJavascriptFromCharCode() throws Exception {
        PaperVO paper = paper(List.of(question(1, 1, "custom", "实体 &#999999999999999999999999999999999999999999999999999999; 结束", "{}", "{}")));

        String xml = assertDoesNotThrow(() -> renderer.renderQuestions(paper, layout()));
        parse(xml);
        assertFalse(xml.contains("&#999999999999999999999999999999999999999999999999999999;"));
    }

    @Test
    void rendersSubtitleAndExactResolvedPageSettingsAsParseableFragments() throws Exception {
        PaperVO paper = paper(List.of());
        paper.setSubtitle("全卷 <100> & 60分钟");
        PaperLayoutConfig config = layout();
        SourcePaperLayoutResolver.ResolvedPageLayout resolved = new SourcePaperLayoutResolver().resolve(config);

        String subtitle = renderer.renderSubtitle(paper, config);
        parse(subtitle);
        // 可配置标题/副标题/正文字号是产品明确要求迁移的源码配置能力。
        assertTrue(subtitle.contains("w:sz w:val=\"24\""));
        assertTrue(subtitle.contains("全卷 &lt;100&gt; &amp; 60分钟"));

        String page = renderer.renderPageSettings(resolved);
        parse(page);
        assertEquals(resolved.pageSizeXml() + resolved.pageMarginsXml() + resolved.columnsXml() + resolved.documentGridXml(), page);
    }

    private static PaperVO paper(List<QuestionSnapshotVO> questions) {
        PaperVO paper = new PaperVO();
        paper.setQuestions(questions);
        return paper;
    }

    private static QuestionSnapshotVO question(int sort, int section, String type, String stem, String body, String answer) {
        QuestionSnapshotVO question = new QuestionSnapshotVO();
        question.setSortOrder(sort);
        question.setSectionOrder(section);
        question.setType(type);
        question.setStem(stem);
        question.setScore(new BigDecimal("5"));
        question.setBodyJson(body);
        question.setAnswerJson(answer);
        return question;
    }

    private static String options(String... values) {
        StringBuilder json = new StringBuilder("{\"options\":[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) json.append(',');
            json.append("{\"key\":\"").append((char) ('A' + index)).append("\",\"text\":\"")
                    .append(values[index]).append("\"}");
        }
        return json.append("]}").toString();
    }

    private static PaperLayoutConfig layout() {
        return new PaperLayoutConfig();
    }

    private static Document parse(String fragment) throws Exception {
        String wrapped = "<root xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" + fragment + "</root>";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));
    }

    private static int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
