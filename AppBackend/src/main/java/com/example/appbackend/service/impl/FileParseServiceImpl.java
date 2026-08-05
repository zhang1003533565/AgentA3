package com.example.appbackend.service.impl;

import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.FileParseService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

@Service
public class FileParseServiceImpl implements FileParseService {
    private static final int MAX_PARSED_TEXT_LENGTH = 120_000;

    @Override
    public String parse(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new BusinessException(400, "文件不存在");
        }
        String extension = extensionOf(file.getName());
        try {
            String text = switch (extension) {
                case ".pdf" -> parsePdf(file);
                case ".docx" -> parseDocx(file);
                case ".doc" -> parseDoc(file);
                case ".pptx" -> parsePptx(file);
                case ".ppt" -> parsePpt(file);
                case ".md", ".markdown" -> parseMarkdown(file);
                default -> throw new BusinessException(400, "仅支持 PDF、Word、PPT、Markdown 文件");
            };
            return normalizeText(text);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "文件解析失败: " + error.getMessage());
        }
    }

    @Override
    public String parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        String extension = extensionOf(file.getOriginalFilename());
        try (InputStream input = file.getInputStream()) {
            String text = switch (extension) {
                case ".pdf" -> parsePdf(input);
                case ".docx" -> parseDocx(input);
                case ".doc" -> parseDoc(input);
                case ".pptx" -> parsePptx(input);
                case ".ppt" -> parsePpt(input);
                case ".md", ".markdown" -> parseMarkdown(input);
                default -> throw new BusinessException(400, "仅支持 PDF、Word、PPT、Markdown 文件");
            };
            return normalizeText(text);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "文件解析失败: " + error.getMessage());
        }
    }

    private String parsePdf(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parsePdf(InputStream input) throws Exception {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseDocx(File file) throws Exception {
        try (XWPFDocument document = new XWPFDocument(java.nio.file.Files.newInputStream(file.toPath()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDocx(InputStream input) throws Exception {
        try (XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDoc(File file) throws Exception {
        try (HWPFDocument document = new HWPFDocument(java.nio.file.Files.newInputStream(file.toPath()));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDoc(InputStream input) throws Exception {
        try (HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parsePptx(File file) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(java.nio.file.Files.newInputStream(file.toPath()))) {
            return parsePptx(slideShow);
        }
    }

    private String parsePptx(InputStream input) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(input)) {
            return parsePptx(slideShow);
        }
    }

    private String parsePptx(XMLSlideShow slideShow) {
        StringBuilder builder = new StringBuilder();
        for (XSLFSlide slide : slideShow.getSlides()) {
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    appendLine(builder, textShape.getText());
                }
            }
        }
        return builder.toString();
    }

    private String parsePpt(File file) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(java.nio.file.Files.newInputStream(file.toPath()))) {
            return parsePpt(slideShow);
        }
    }

    private String parsePpt(InputStream input) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(input)) {
            return parsePpt(slideShow);
        }
    }

    private String parsePpt(HSLFSlideShow slideShow) {
        StringBuilder builder = new StringBuilder();
        for (HSLFSlide slide : slideShow.getSlides()) {
            for (HSLFShape shape : slide.getShapes()) {
                if (shape instanceof HSLFTextShape textShape) {
                    appendLine(builder, textShape.getText());
                }
            }
        }
        return builder.toString();
    }

    private String parseMarkdown(File file) throws Exception {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private String parseMarkdown(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String normalizeText(String value) {
        String text = value == null ? "" : value.replace('\u0000', ' ').trim();
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(400, "文件未解析到可用文本");
        }
        return text.length() > MAX_PARSED_TEXT_LENGTH ? text.substring(0, MAX_PARSED_TEXT_LENGTH) : text;
    }

    private void appendLine(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text.trim()).append('\n');
        }
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }
}
