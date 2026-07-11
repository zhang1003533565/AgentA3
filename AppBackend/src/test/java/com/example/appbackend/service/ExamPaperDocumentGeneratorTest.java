package com.example.appbackend.service;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColumns;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

class ExamPaperDocumentGeneratorTest {

    @Test
    void generatesReadablePaperWithoutAnswers() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, "single_choice", "第一道题", "{\"options\":[{\"key\":\"A\",\"text\":\"甲\"},{\"key\":\"B\",\"text\":\"乙\"}]}", "{\"correctOption\":\"A\"}", "因为甲正确"),
                question(2, "true_false", "第二道题", "{\"statement\":\"天空是蓝色\"}", "{\"correct\":true}", null),
                question(3, "short_answer", "第三道题", "{}", "{\"referenceAnswer\":\"参考内容\",\"answerPoints\":[\"要点一\"]}", null)
        ));

        String text = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.PAPER));

        assertTrue(text.contains("期末考试"));
        assertTrue(text.contains("第一道题"));
        assertTrue(text.contains("A. 甲"));
        assertTrue(text.contains("B. 乙"));
        assertTrue(text.contains("第二道题"));
        assertTrue(text.contains("第三道题"));
        assertFalse(text.contains("标准答案"));
        assertFalse(text.contains("参考答案"));
        assertFalse(text.contains("因为甲正确"));
        assertFalse(text.contains("参考内容"));
    }

    @Test
    void groupsInterleavedTypesByStableSectionOrderWithCountsAndScores() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, 1, "single_choice", "A一", "{\"options\":[{\"key\":\"A\",\"text\":\"甲\"}]}", "{}", null),
                question(2, 2, "true_false", "B二", "{\"statement\":\"判断\"}", "{}", null),
                question(3, 1, "single_choice", "A三", "{\"options\":[{\"key\":\"B\",\"text\":\"乙\"}]}", "{}", null)
        ));

        String text = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.PAPER));

        int first = text.indexOf("1. A一");
        int second = text.indexOf("2. A三");
        int third = text.indexOf("3. B二");
        assertTrue(first >= 0 && first < second && second < third, text);
        assertTrue(text.contains("单项选择题（共2题，10分）"), text);
        assertTrue(text.contains("判断题（共1题，5分）"), text);
        assertEquals(1, occurrences(text, "单项选择题"));
        assertEquals(1, occurrences(text, "判断题"));
    }

    @Test
    void paperAndAnswerKeepTheSameGroupedQuestionNumbers() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, 1, "single_choice", "A一", "{}", "{\"correctOption\":\"A\"}", null),
                question(2, 2, "true_false", "B二", "{}", "{\"correct\":true}", null),
                question(3, 1, "single_choice", "A三", "{}", "{\"correctOption\":\"B\"}", null)
        ));

        String paperText = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.PAPER));
        String answerText = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.ANSWER));

        for (String numberedStem : List.of("1. A一", "2. A三", "3. B二")) {
            assertTrue(paperText.contains(numberedStem), paperText);
            assertTrue(answerText.contains(numberedStem), answerText);
        }
    }

    @Test
    void doesNotRenderEmptyObjectArrayOrNullBodies() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, 1, "short_answer", "空对象", "{}", "{}", null),
                question(2, 1, "short_answer", "空数组", "[]", "{}", null),
                question(3, 1, "short_answer", "空值", "null", "{}", null)
        ));

        String text = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.PAPER));

        assertFalse(text.contains("{}"), text);
        assertFalse(text.contains("[]"), text);
        assertFalse(text.lines().anyMatch("null"::equals), text);
    }

    @Test
    void fallsBackToPrettyJsonWhenValidBodyHasNoKnownRenderableFields() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, "custom", "未知正文", "{\"diagram\":{\"label\":\"示意图\"},\"values\":[1,2]}", "{}", null)
        ));

        String text = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.PAPER));

        assertTrue(text.contains("\"diagram\""));
        assertTrue(text.contains("\"示意图\""));
        assertTrue(text.contains("\"values\""));
    }

    @Test
    void answerKeepsNumbersAndRendersKnownAndStructuredAnswers() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, "single_choice", "选择题", "{\"options\":[{\"key\":\"A\",\"text\":\"甲\"}]}", "{\"correctOption\":\"A\"}", "解析甲"),
                question(2, "true_false", "判断题", "{}", "{\"correct\":false}", null),
                question(3, "custom", "自定义题", "{}", "{\"points\":[\"一\",\"二\"]}", null)
        ));

        String text = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.ANSWER));

        assertTrue(text.contains("1. 选择题"));
        assertTrue(text.contains("2. 判断题"));
        assertTrue(text.contains("3. 自定义题"));
        assertTrue(text.contains("标准答案：A"));
        assertTrue(text.contains("标准答案：错误"));
        assertTrue(text.contains("\"points\""));
        assertTrue(text.contains("解析：解析甲"));
    }

    @Test
    void malformedJsonFallsBackToTextAndDoesNotAbortDocument() throws Exception {
        PaperVO paper = paper(PageSize.A4, Orientation.PORTRAIT, 1);
        paper.setQuestions(List.of(
                question(1, "single_choice", "坏数据题", "not-json", "{bad", null),
                question(2, "short_answer", "后续题", "{}", "{\"referenceAnswer\":\"仍然输出\"}", null)
        ));

        String text = text(new ExamPaperDocumentGenerator().generate(paper, DownloadContent.ANSWER));

        assertTrue(text.contains("not-json"));
        assertTrue(text.contains("{bad"));
        assertTrue(text.contains("2. 后续题"));
        assertTrue(text.contains("仍然输出"));
    }

    @ParameterizedTest(name = "{0}-{1}-{2}栏")
    @MethodSource("layouts")
    void persistsPageAndColumnConfiguration(PageSize size, Orientation orientation, int columns,
                                            long portraitWidth, long portraitHeight) throws Exception {
        byte[] bytes = new ExamPaperDocumentGenerator().generate(paper(size, orientation, columns), DownloadContent.PAPER);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            CTSectPr section = document.getDocument().getBody().getSectPr();
            long expectedWidth = orientation == Orientation.LANDSCAPE ? portraitHeight : portraitWidth;
            long expectedHeight = orientation == Orientation.LANDSCAPE ? portraitWidth : portraitHeight;
            assertEquals(BigInteger.valueOf(expectedWidth), section.getPgSz().getW());
            assertEquals(BigInteger.valueOf(expectedHeight), section.getPgSz().getH());
            if (orientation == Orientation.LANDSCAPE) {
                assertEquals("landscape", section.getPgSz().getOrient().toString());
            } else {
                assertTrue(!section.getPgSz().isSetOrient()
                                || "portrait".equals(section.getPgSz().getOrient().toString()),
                        "portrait 页面应省略方向或明确标记 portrait");
            }
            CTColumns cols = section.getCols();
            assertEquals(BigInteger.valueOf(columns), cols.getNum());
            assertEquals(BigInteger.valueOf(425), cols.getSpace());
            assertEquals(columns == 2, cols.getSep());
        }
    }

    private static Stream<Arguments> layouts() {
        return Stream.of(PageSize.values()).flatMap(size -> Stream.of(Orientation.values()).flatMap(orientation ->
                Stream.of(1, 2).map(columns -> {
                    long[] dimensions = switch (size) {
                        case A3 -> new long[]{16838, 23811};
                        case A4 -> new long[]{11906, 16838};
                        case B4 -> new long[]{14173, 20013};
                    };
                    return Arguments.of(size, orientation, columns, dimensions[0], dimensions[1]);
                })));
    }

    private static PaperVO paper(PageSize size, Orientation orientation, int columns) {
        PaperVO paper = new PaperVO();
        paper.setTitle("期末考试");
        paper.setSubtitle("2026 学年");
        paper.setHeaderInfo("姓名：________ 学号：________");
        paper.setDurationMinutes(120);
        paper.setTotalScore(new BigDecimal("100"));
        paper.setPrecautions("请认真作答");
        paper.setPageSize(size);
        paper.setOrientation(orientation);
        paper.setColumnsCount(columns);
        paper.setQuestions(List.of(question(1, "short_answer", "第一道题", "{}", "{}", null)));
        return paper;
    }

    private static QuestionSnapshotVO question(int order, String type, String stem, String body, String answer, String analysis) {
        return question(order, order, type, stem, body, answer, analysis);
    }

    private static QuestionSnapshotVO question(int order, int sectionOrder, String type, String stem, String body, String answer, String analysis) {
        QuestionSnapshotVO question = new QuestionSnapshotVO();
        question.setSortOrder(order);
        question.setSectionOrder(sectionOrder);
        question.setScore(new BigDecimal("5"));
        question.setType(type);
        question.setStem(stem);
        question.setBodyJson(body);
        question.setAnswerJson(answer);
        question.setAnalysis(analysis);
        return question;
    }

    private static String text(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream().map(XWPFParagraph::getText).collect(joining("\n"));
        }
    }

    private static int occurrences(String text, String fragment) {
        return (text.length() - text.replace(fragment, "").length()) / fragment.length();
    }
}
