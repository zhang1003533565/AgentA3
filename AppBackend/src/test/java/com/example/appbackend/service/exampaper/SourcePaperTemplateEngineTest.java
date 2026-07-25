package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.service.ExamPaperDocumentGenerator;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SourcePaperTemplateEngineTest {

    private final SourcePaperTemplateEngine engine = new SourcePaperTemplateEngine();

    @Test
    void generatesSourceFaithfulPackageAndPreservesOpaqueParts() throws Exception {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        layout.setHeaderInfo("矿井甲    姓名________");
        byte[] generated = engine.generate(paper(), DownloadContent.PAPER, layout);
        Map<String, byte[]> entries = entries(generated);

        SourcePaperPackageVerifier.verify(generated);
        String document = text(entries, "word/document.xml");
        String header1 = text(entries, "word/header1.xml");
        String header2 = text(entries, "word/header2.xml");
        String settings = text(entries, "word/settings.xml");
        assertFalse(document.matches("(?s).*%[^%]+%.*"));
        assertFalse(header1.matches("(?s).*%[^%]+%.*"));
        assertFalse(header2.matches("(?s).*%[^%]+%.*"));
        assertTrue(document.contains("w:w=\"23814\" w:h=\"16840\" w:orient=\"landscape\""));
        assertTrue(document.contains("w:left=\"2500\""));
        assertTrue(document.contains("w:num=\"2\" w:space=\"425\" w:sep=\"1\""));
        assertTrue(document.contains("w:type=\"default\" r:id=\"rId8\""));
        assertTrue(document.contains("w:type=\"even\" r:id=\"rId9\""));
        assertTrue(settings.contains("w:evenAndOddHeaders"));
        assertFalse(header1.contains("矿井甲"), "权威 header1.xml 没有 information 插槽");
        assertTrue(header2.contains("矿井甲"));

        Map<String, byte[]> source = entries(resource("exam-paper-template/static/document.docx"));
        for (String name : source.keySet()) {
            if (!SourcePaperPackageVerifier.MUTABLE_PARTS.contains(name)) {
                assertEquals(sha256(source.get(name)), sha256(entries.get(name)), name);
            }
        }
    }

    @Test
    void separatesPaperAndAnswerContentWithoutChangingPackageFurniture() throws Exception {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        Map<String, byte[]> paper = entries(engine.generate(paper(), DownloadContent.PAPER, layout));
        Map<String, byte[]> answer = entries(engine.generate(paper(), DownloadContent.ANSWER, layout));

        String paperXml = text(paper, "word/document.xml");
        String answerXml = text(answer, "word/document.xml");
        assertFalse(paperXml.contains("答案解析"));
        assertTrue(answerXml.contains("答案解析"));
        assertTrue(answerXml.contains("答案:A"));
        for (String name : paper.keySet()) {
            if (!name.equals("word/document.xml")) {
                assertArrayEquals(paper.get(name), answer.get(name), name);
            }
        }
    }

    @Test
    void preservesPercentagesAndPlaceholderLikeTextFromPaperContent() throws Exception {
        PaperVO paper = paper();
        paper.setTitle("达标率 80%，优秀率 20%");
        paper.setPrecautions("请保留字面文本 %TITLE%，不要把它当成模板指令");
        paper.setHeaderInfo("完成率 100%  姓名________");
        paper.getQuestions().getFirst().setStem("某班及格率为 75%，优秀率为 25%；保留 %QUESTION% 字样。");

        String document = text(entries(engine.generate(
                paper, DownloadContent.PAPER, new PaperLayoutConfig())), "word/document.xml");

        assertTrue(document.contains("80%"));
        assertTrue(document.contains("20%"));
        assertTrue(document.contains("%TITLE%"));
        assertTrue(document.contains("%QUESTION%"));
    }

    @Test
    void dispatcherKeepsSimpleModeAndRoutesTemplateMode() throws Exception {
        ExamPaperDocumentDispatcher dispatcher = new ExamPaperDocumentDispatcher(
                new ExamPaperDocumentGenerator(), engine);
        PaperLayoutConfig simple = new PaperLayoutConfig();
        simple.setRenderMode(PaperRenderMode.SIMPLE);
        simple.setPageSize(PageSize.B4);
        simple.setOrientation(Orientation.LANDSCAPE);
        simple.setMarginPreset(MarginPreset.CUSTOM);
        simple.setCustomMarginTop(701);
        simple.setCustomMarginRight(702);
        simple.setCustomMarginBottom(703);
        simple.setCustomMarginLeft(704);
        simple.setColumnSpace(733);
        simple.setHasBindingLine(false);
        PaperLayoutConfig template = new PaperLayoutConfig();

        byte[] simpleBytes = dispatcher.generate(paper(), DownloadContent.PAPER, simple);
        byte[] templateBytes = dispatcher.generate(paper(), DownloadContent.PAPER, template);

        assertNotEquals(sha256(simpleBytes), sha256(templateBytes));
        try (XWPFDocument simpleDocument = new XWPFDocument(new ByteArrayInputStream(simpleBytes))) {
            var section = simpleDocument.getDocument().getBody().getSectPr();
            assertEquals("20639", section.getPgSz().getW().toString());
            assertEquals("14572", section.getPgSz().getH().toString());
            assertEquals("701", section.getPgMar().getTop().toString());
            assertEquals("733", section.getCols().getSpace().toString());
            assertTrue(simpleDocument.getHeaderList().isEmpty());
        }
        assertDoesNotThrow(() -> SourcePaperPackageVerifier.verify(templateBytes));
    }

    @Test
    void rejectsTemplateWithMissingOrUnexpectedPlaceholderCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> SourcePaperTemplateEngine.replaceRequired("before", "%TITLE%", "x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> SourcePaperTemplateEngine.replaceRequired("%TITLE% %TITLE%", "%TITLE%", "x", 1));
        assertEquals("x x", SourcePaperTemplateEngine.replaceRequired(
                "%information% %information%", "%information%", "x", 2));
    }

    @Test
    void appliesNonDefaultTitleSizeOnlyInsideLockedTitleStructure() throws Exception {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        layout.setTitleFontSize(62);
        String document = text(entries(engine.generate(paper(), DownloadContent.PAPER, layout)), "word/document.xml");
        int title = document.indexOf("源码版式测试");
        String titleParagraph = document.substring(document.lastIndexOf("<w:p ", title), document.indexOf("</w:p>", title));
        assertEquals(2, occurrences(titleParagraph, "<w:sz w:val=\"62\"/>"));
        assertEquals(2, occurrences(titleParagraph, "<w:szCs w:val=\"62\"/>"));
        assertFalse(titleParagraph.contains("w:val=\"50\""));
    }

    @Test
    void rejectsAnyHeaderTokenMultiplicityDrift() throws Exception {
        String header1 = new String(resource("exam-paper-template/head/header1.xml"), StandardCharsets.UTF_8);
        String header2 = new String(resource("exam-paper-template/head/header2.xml"), StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> SourcePaperTemplateEngine.verifyHeaderTemplateContract(header1, header2));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperTemplateEngine.verifyHeaderTemplateContract(
                header1 + "%h1LineTop%", header2));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperTemplateEngine.verifyHeaderTemplateContract(
                header1, header2.replaceFirst("%information%", "")));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperTemplateEngine.verifyHeaderTemplateContract(
                header1 + "%information%", header2));
    }

    @Test
    void verifierRejectsMalformedSettingsAndFixedRelationshipDrift() throws Exception {
        byte[] valid = engine.generate(paper(), DownloadContent.PAPER, new PaperLayoutConfig());
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(
                replaceEntry(valid, "word/settings.xml", "<w:settings>".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/settings.xml", ("<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "</w:settings>").getBytes(StandardCharsets.UTF_8))));
        Map<String, byte[]> parts = entries(valid);
        String relationships = text(parts, "word/_rels/document.xml.rels");
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/_rels/document.xml.rels", relationships.replace("Target=\"header1.xml\"", "Target=\"headerX.xml\"")
                        .getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/_rels/document.xml.rels", relationships.replace(
                                "relationships/footer\" Target=\"footer1.xml\"",
                                "relationships/header\" Target=\"footer1.xml\"")
                        .getBytes(StandardCharsets.UTF_8))));
        String document = text(parts, "word/document.xml");
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/document.xml", document.replace("r:id=\"rId8\"", "r:id=\"rId13\"")
                        .getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void bindingDisabledKeepsFixedDefinitionsButRemovesAllFourDocumentReferences() throws Exception {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        layout.setHasBindingLine(false);
        byte[] generated = engine.generate(paper(), DownloadContent.PAPER, layout);
        Map<String, byte[]> parts = entries(generated);
        String document = text(parts, "word/document.xml");
        String relationships = text(parts, "word/_rels/document.xml.rels");
        for (String id : java.util.List.of("rId8", "rId9", "rId10", "rId11")) {
            assertFalse(document.contains("r:id=\"" + id + "\""));
            assertTrue(relationships.contains("Id=\"" + id + "\""));
        }
        assertDoesNotThrow(() -> SourcePaperPackageVerifier.verify(generated));
    }

    @Test
    void verifierRejectsMixedAllPresentOrAllAbsentReferenceState() throws Exception {
        byte[] valid = engine.generate(paper(), DownloadContent.PAPER, new PaperLayoutConfig());
        Map<String, byte[]> parts = entries(valid);
        String document = text(parts, "word/document.xml");
        String withoutOneReference = document.replace(
                "<w:headerReference w:type=\"default\" r:id=\"rId8\"/>", "");
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(
                valid, "word/document.xml", withoutOneReference.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void verifierRejectsExtraAndCrossKindHeaderFooterReferences() throws Exception {
        byte[] valid = engine.generate(paper(), DownloadContent.PAPER, new PaperLayoutConfig());
        String document = text(entries(valid), "word/document.xml");
        String defaultHeader = "<w:headerReference w:type=\"default\" r:id=\"rId8\"/>";
        String defaultFooter = "<w:footerReference w:type=\"default\" r:id=\"rId10\"/>";

        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/document.xml", document.replace(defaultHeader,
                                defaultHeader + "<w:headerReference w:type=\"default\" r:id=\"rId13\"/>")
                        .getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/document.xml", document.replace(defaultHeader,
                                "<w:headerReference w:type=\"default\" r:id=\"rId10\"/>")
                        .getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> SourcePaperPackageVerifier.verify(replaceEntry(valid,
                "word/document.xml", document.replace(defaultFooter,
                                "<w:footerReference w:type=\"default\" r:id=\"rId8\"/>")
                        .getBytes(StandardCharsets.UTF_8))));
    }

    private PaperVO paper() {
        PaperVO paper = new PaperVO();
        paper.setTitle("源码版式测试");
        paper.setSubtitle("(全卷满分: 5分，考试时间: 60分钟)");
        paper.setPrecautions("请认真作答");
        paper.setHeaderInfo("矿井甲    姓名________");
        paper.setTotalScore(new BigDecimal("5"));
        QuestionSnapshotVO question = new QuestionSnapshotVO();
        question.setSortOrder(1);
        question.setSectionOrder(1);
        question.setType("single_choice");
        question.setStem("1&lt;2 是否成立？");
        question.setScore(new BigDecimal("5"));
        question.setBodyJson("{\"options\":[{\"key\":\"A\",\"text\":\"是\"},{\"key\":\"B\",\"text\":\"否\"}]}");
        question.setAnswerJson("{\"correctOption\":\"A\"}");
        paper.setQuestions(java.util.List.of(question));
        return paper;
    }

    private static byte[] resource(String name) throws Exception {
        try (var input = SourcePaperTemplateEngineTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name);
            return input.readAllBytes();
        }
    }

    private static Map<String, byte[]> entries(byte[] bytes) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) result.put(entry.getName(), input.readAllBytes());
            }
        }
        return result;
    }

    private static String text(Map<String, byte[]> entries, String name) {
        assertNotNull(entries.get(name), name);
        return new String(entries.get(name), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] replaceEntry(byte[] source, String target, byte[] replacement) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source));
             java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) output.write(entry.getName().equals(target) ? replacement : input.readAllBytes());
                output.closeEntry();
            }
            output.finish();
            return bytes.toByteArray();
        }
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
