package com.example.appbackend.service.impl;

import com.example.appbackend.service.ParsedFileContent;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class FileParseServiceImplTest {

    private final FileParseServiceImpl service = new FileParseServiceImpl();

    @TempDir
    Path tempDir;

    @Test
    void parseDocxReadsParagraphsAndTableCells() throws Exception {
        Path file = tempDir.resolve("course.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("计算机课程体系");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("基础课程");
            table.getRow(0).getCell(1).setText("程序设计");
            table.getRow(1).getCell(0).setText("核心课程");
            table.getRow(1).getCell(1).setText("操作系统");
            try (OutputStream output = Files.newOutputStream(file)) {
                document.write(output);
            }
        }

        ParsedFileContent parsed = service.parseDetailed(file.toFile());

        Assertions.assertTrue(parsed.text().contains("计算机课程体系"));
        Assertions.assertTrue(parsed.text().contains("基础课程 | 程序设计"));
        Assertions.assertTrue(parsed.text().contains("核心课程 | 操作系统"));
        Assertions.assertTrue(parsed.paragraphCount() >= 3);
    }

    @Test
    void parsePptxKeepsSlideMarkersAndText() throws Exception {
        Path file = tempDir.resolve("architecture.pptx");
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText("用户层 接入层 服务层 数据层");
            try (OutputStream output = Files.newOutputStream(file)) {
                slideShow.write(output);
            }
        }

        ParsedFileContent parsed = service.parseDetailed(file.toFile());

        Assertions.assertEquals(1, parsed.slideCount());
        Assertions.assertTrue(parsed.text().contains("第 1 张幻灯片"));
        Assertions.assertTrue(parsed.text().contains("用户层 接入层 服务层 数据层"));
    }

    @Test
    void parseMarkdownReportsOriginalLengthAndTruncatesSafely() throws Exception {
        Path file = tempDir.resolve("large.md");
        Files.writeString(file, "a".repeat(130_000), StandardCharsets.UTF_8);

        ParsedFileContent parsed = service.parseDetailed(file.toFile());

        Assertions.assertEquals(130_000, parsed.textLength());
        Assertions.assertEquals(120_000, parsed.text().length());
        Assertions.assertTrue(parsed.truncated());
    }
}
