package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionGenerationMaterialParserTest {

    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;
    private final QuestionGenerationMaterialParser parser =
            new QuestionGenerationMaterialParser(MAX_FILE_BYTES, 200_000);

    @Test
    void springCanInstantiateParserWithConfiguredLimits() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(QuestionGenerationMaterialParser.class);
            context.refresh();

            assertThat(context.getBean(QuestionGenerationMaterialParser.class)).isNotNull();
        }
    }

    @Test
    void parsesUtf8TxtAndRemovesBom() {
        byte[] content = ("\uFEFF第一章\n核心概念").getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "课程.txt", "text/plain", content);

        ParsedMaterial result = parser.parse("txt", file, null);

        assertThat(result.text()).isEqualTo("第一章\n核心概念");
        assertThat(result.originalFilename()).isEqualTo("课程.txt");
        assertThat(result.sourceTitle()).isEqualTo("课程");
    }

    @Test
    void rejectsMalformedUtf8Txt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "课程.txt", "text/plain", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> parser.parse("txt", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void rejectsEmptyTextContent() {
        assertThatThrownBy(() -> parser.parse("text", null, "  \n\t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");
    }

    @Test
    void rejectsFilesLargerThanTenMibibytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "课程.txt", "text/plain", new byte[(int) MAX_FILE_BYTES + 1]);

        assertThatThrownBy(() -> parser.parse("txt", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10485760");
    }

    @Test
    void rejectsTextLongerThanConfiguredCharacterLimit() {
        QuestionGenerationMaterialParser shortParser = new QuestionGenerationMaterialParser(MAX_FILE_BYTES, 4);

        assertThatThrownBy(() -> shortParser.parse("text", null, "五个字符啊"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4");
    }

    @Test
    void rejectsDocxZipEntryLargerThanConfiguredLimitBeforePoiParsing() throws Exception {
        QuestionGenerationMaterialParser limited = new QuestionGenerationMaterialParser(
                MAX_FILE_BYTES, 200_000, 100, 1024, 20);
        MockMultipartFile file = docx("课程.docx", document ->
                document.createParagraph().createRun().setText("x".repeat(500)));

        assertThatThrownBy(() -> limited.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZIP 条目");
    }

    @Test
    void stopsDocxTextExtractionAsSoonAsCharacterLimitIsExceeded() throws Exception {
        QuestionGenerationMaterialParser limited = new QuestionGenerationMaterialParser(
                MAX_FILE_BYTES, 10, 1024 * 1024, 2 * 1024 * 1024, 2000);
        MockMultipartFile file = docx("课程.docx", document -> {
            document.createParagraph().createRun().setText("12345678901");
            document.createParagraph().createRun().setText("不应继续累积");
        });

        assertThatThrownBy(() -> limited.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    void parsesDocxParagraphsAndTablesInDocumentOrder() throws Exception {
        MockMultipartFile file = docx("课程.docx", document -> {
            document.createParagraph().createRun().setText("第一章");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("概念");
            table.getRow(0).getCell(1).setText("定义");
            document.createParagraph().createRun().setText("本章小结");
        });

        ParsedMaterial result = parser.parse("docx", file, null);

        assertThat(result.text()).containsSubsequence("第一章", "概念", "定义", "本章小结");
        assertThat(result.originalFilename()).isEqualTo("课程.docx");
        assertThat(result.sourceTitle()).isEqualTo("课程");
    }

    @Test
    void rejectsCorruptedDocx() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "课程.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a zip package".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCX");
    }

    @Test
    void rejectsDocxWithoutTextContentAsEmpty() throws Exception {
        MockMultipartFile file = docx("空白.docx", document -> {
        });

        assertThatThrownBy(() -> parser.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");
    }

    @Test
    void rejectsLegacyDocFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "课程.doc", "application/msword", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> parser.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".doc");
    }

    @Test
    void rejectsPdfFilesDeclaredAsDocx() throws Exception {
        MockMultipartFile file = docx("课程.pdf", document ->
                document.createParagraph().createRun().setText("第一章"));

        assertThatThrownBy(() -> parser.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".docx");
    }

    @Test
    void rejectsPptxFilesDeclaredAsDocx() throws Exception {
        MockMultipartFile file = docx("课程.pptx", document ->
                document.createParagraph().createRun().setText("第一章"));

        assertThatThrownBy(() -> parser.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".docx");
    }

    @Test
    void rejectsDocxExtensionDeclaredAsTxt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "课程.docx", "text/plain", "第一章".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse("txt", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".txt");
    }

    @Test
    void rejectsTxtExtensionDeclaredAsDocx() throws Exception {
        MockMultipartFile file = docx("课程.txt", document ->
                document.createParagraph().createRun().setText("第一章"));

        assertThatThrownBy(() -> parser.parse("docx", file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".docx");
    }

    private MockMultipartFile docx(String filename, Consumer<XWPFDocument> writer) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.accept(document);
            document.write(output);
            return new MockMultipartFile(
                    "file", filename,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    output.toByteArray());
        }
    }
}
