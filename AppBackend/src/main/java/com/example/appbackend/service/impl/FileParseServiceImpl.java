package com.example.appbackend.service.impl;

import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.FileParseService;
import com.example.appbackend.service.ParsedFileContent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFGroupShape;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileParseServiceImpl implements FileParseService {
    private static final int MAX_PARSED_TEXT_LENGTH = 120_000;

    @Override
    public String parse(File file) {
        return parseDetailed(file).text();
    }

    @Override
    public String parse(MultipartFile file) {
        return parseDetailed(file).text();
    }

    @Override
    public ParsedFileContent parseDetailed(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new BusinessException(400, "文件不存在");
        }
        String extension = extensionOf(file.getName());
        ParseStats stats = new ParseStats();
        try {
            String resolvedExtension = resolveOfficeExtension(extension, file);
            String text = switch (resolvedExtension) {
                case ".pdf" -> parsePdf(file, stats);
                case ".docx" -> parseDocx(file, stats);
                case ".doc" -> parseDoc(file, stats);
                case ".pptx" -> parsePptx(file, stats);
                case ".ppt" -> parsePpt(file, stats);
                case ".md", ".markdown" -> parseTextFile(file, stats);
                default -> throw new BusinessException(400, "仅支持 PDF、Word、PPT、Markdown 文件");
            };
            return normalizeText(text, stats);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "文件解析失败: " + error.getMessage());
        }
    }

    @Override
    public ParsedFileContent parseDetailed(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        String extension = extensionOf(file.getOriginalFilename());
        ParseStats stats = new ParseStats();
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            String resolvedExtension = resolveOfficeExtension(extension, bytes);
            String text = switch (resolvedExtension) {
                case ".pdf" -> parsePdf(new ByteArrayInputStream(bytes), stats);
                case ".docx" -> parseDocx(new ByteArrayInputStream(bytes), stats);
                case ".doc" -> parseDoc(new ByteArrayInputStream(bytes), stats);
                case ".pptx" -> parsePptx(new ByteArrayInputStream(bytes), stats);
                case ".ppt" -> parsePpt(new ByteArrayInputStream(bytes), stats);
                case ".md", ".markdown" -> parseTextFile(new ByteArrayInputStream(bytes), stats);
                default -> throw new BusinessException(400, "仅支持 PDF、Word、PPT、Markdown 文件");
            };
            return normalizeText(text, stats);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "文件解析失败: " + error.getMessage());
        }
    }

    private String parsePdf(File file, ParseStats stats) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            return parsePdf(document, stats);
        }
    }

    private String parsePdf(InputStream input, ParseStats stats) throws Exception {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            return parsePdf(document, stats);
        }
    }

    private String parsePdf(PDDocument document, ParseStats stats) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        StringBuilder builder = new StringBuilder();
        int pageCount = document.getNumberOfPages();
        stats.pageCount = pageCount;
        for (int page = 1; page <= pageCount; page++) {
            appendSection(builder, "第 " + page + " 页");
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            appendParagraph(builder, stripper.getText(document), stats);
        }
        return builder.toString();
    }

    private String parseDocx(File file, ParseStats stats) throws Exception {
        try (XWPFDocument document = new XWPFDocument(java.nio.file.Files.newInputStream(file.toPath()))) {
            return parseDocx(document, stats);
        }
    }

    private String parseDocx(InputStream input, ParseStats stats) throws Exception {
        try (XWPFDocument document = new XWPFDocument(input)) {
            return parseDocx(document, stats);
        }
    }

    private String parseDocx(XWPFDocument document, ParseStats stats) {
        StringBuilder builder = new StringBuilder();
        for (IBodyElement element : document.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH && element instanceof XWPFParagraph paragraph) {
                appendParagraph(builder, paragraph.getText(), stats);
            } else if (element.getElementType() == BodyElementType.TABLE && element instanceof XWPFTable table) {
                appendDocxTable(builder, table, stats);
            }
        }
        return builder.toString();
    }

    private void appendDocxTable(StringBuilder builder, XWPFTable table, ParseStats stats) {
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder rowText = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = extractDocxCellText(cell);
                if (StringUtils.hasText(cellText)) {
                    if (!rowText.isEmpty()) {
                        rowText.append(" | ");
                    }
                    rowText.append(cellText.trim());
                }
            }
            appendParagraph(builder, rowText.toString(), stats);
        }
    }

    private String extractDocxCellText(XWPFTableCell cell) {
        StringBuilder builder = new StringBuilder();
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            appendRawLine(builder, paragraph.getText());
        }
        for (XWPFTable table : cell.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                StringBuilder rowText = new StringBuilder();
                for (XWPFTableCell nestedCell : row.getTableCells()) {
                    String text = extractDocxCellText(nestedCell);
                    if (StringUtils.hasText(text)) {
                        if (!rowText.isEmpty()) {
                            rowText.append(" | ");
                        }
                        rowText.append(text.trim());
                    }
                }
                appendRawLine(builder, rowText.toString());
            }
        }
        return builder.toString().trim();
    }

    private String parseDoc(File file, ParseStats stats) throws Exception {
        try (HWPFDocument document = new HWPFDocument(java.nio.file.Files.newInputStream(file.toPath()));
             WordExtractor extractor = new WordExtractor(document)) {
            return parseDoc(extractor, stats);
        }
    }

    private String parseDoc(InputStream input, ParseStats stats) throws Exception {
        try (HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return parseDoc(extractor, stats);
        }
    }

    private String parseDoc(WordExtractor extractor, ParseStats stats) {
        StringBuilder builder = new StringBuilder();
        String[] paragraphs = extractor.getParagraphText();
        if (paragraphs != null && paragraphs.length > 0) {
            for (String paragraph : paragraphs) {
                appendParagraph(builder, paragraph, stats);
            }
            return builder.toString();
        }
        appendParagraph(builder, extractor.getText(), stats);
        return builder.toString();
    }

    private String parsePptx(File file, ParseStats stats) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(java.nio.file.Files.newInputStream(file.toPath()))) {
            return parsePptx(slideShow, stats);
        }
    }

    private String parsePptx(InputStream input, ParseStats stats) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(input)) {
            return parsePptx(slideShow, stats);
        }
    }

    private String parsePptx(XMLSlideShow slideShow, ParseStats stats) {
        StringBuilder builder = new StringBuilder();
        List<XSLFSlide> slides = slideShow.getSlides();
        stats.slideCount = slides.size();
        for (int index = 0; index < slides.size(); index++) {
            XSLFSlide slide = slides.get(index);
            appendSection(builder, "第 " + (index + 1) + " 张幻灯片");
            for (XSLFShape shape : slide.getShapes()) {
                appendPptxShape(builder, shape, stats);
            }
        }
        return builder.toString();
    }

    private void appendPptxShape(StringBuilder builder, XSLFShape shape, ParseStats stats) {
        if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                StringBuilder rowText = new StringBuilder();
                for (XSLFTableCell cell : row.getCells()) {
                    String cellText = cell.getText();
                    if (StringUtils.hasText(cellText)) {
                        if (!rowText.isEmpty()) {
                            rowText.append(" | ");
                        }
                        rowText.append(cellText.trim());
                    }
                }
                appendParagraph(builder, rowText.toString(), stats);
            }
            return;
        }
        if (shape instanceof XSLFTextShape textShape) {
            appendParagraph(builder, textShape.getText(), stats);
            return;
        }
        if (shape instanceof XSLFGroupShape groupShape) {
            for (XSLFShape child : groupShape.getShapes()) {
                appendPptxShape(builder, child, stats);
            }
        }
    }

    private String parsePpt(File file, ParseStats stats) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(java.nio.file.Files.newInputStream(file.toPath()))) {
            return parsePpt(slideShow, stats);
        }
    }

    private String parsePpt(InputStream input, ParseStats stats) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(input)) {
            return parsePpt(slideShow, stats);
        }
    }

    private String parsePpt(HSLFSlideShow slideShow, ParseStats stats) {
        StringBuilder builder = new StringBuilder();
        List<HSLFSlide> slides = slideShow.getSlides();
        stats.slideCount = slides.size();
        for (int index = 0; index < slides.size(); index++) {
            HSLFSlide slide = slides.get(index);
            appendSection(builder, "第 " + (index + 1) + " 张幻灯片");
            for (HSLFShape shape : slide.getShapes()) {
                appendPptShape(builder, shape, stats);
            }
        }
        return builder.toString();
    }

    private void appendPptShape(StringBuilder builder, HSLFShape shape, ParseStats stats) {
        if (shape instanceof HSLFTextShape textShape) {
            appendParagraph(builder, textShape.getText(), stats);
            return;
        }
        if (shape instanceof HSLFGroupShape groupShape) {
            for (HSLFShape child : groupShape.getShapes()) {
                appendPptShape(builder, child, stats);
            }
        }
    }

    private String parseTextFile(File file, ParseStats stats) throws Exception {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        stats.paragraphCount = countTextBlocks(text);
        return text;
    }

    private String parseTextFile(InputStream input, ParseStats stats) throws Exception {
        String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        stats.paragraphCount = countTextBlocks(text);
        return text;
    }

    private ParsedFileContent normalizeText(String value, ParseStats stats) {
        String text = value == null ? "" : value.replace('\u0000', ' ').trim();
        text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(400, "文件未解析到可用文本");
        }
        int originalLength = text.length();
        boolean truncated = originalLength > MAX_PARSED_TEXT_LENGTH;
        String normalizedText = truncated ? text.substring(0, MAX_PARSED_TEXT_LENGTH) : text;
        return new ParsedFileContent(
                normalizedText,
                originalLength,
                truncated,
                stats.pageCount,
                stats.slideCount,
                stats.paragraphCount
        );
    }

    private void appendParagraph(StringBuilder builder, String text, ParseStats stats) {
        if (StringUtils.hasText(text)) {
            builder.append(text.trim()).append('\n');
            stats.paragraphCount++;
        }
    }

    private void appendSection(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append('\n').append("【").append(text.trim()).append("】").append('\n');
        }
    }

    private void appendRawLine(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text.trim()).append('\n');
        }
    }

    private int countTextBlocks(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int count = 0;
        for (String block : text.split("\\R+")) {
            if (StringUtils.hasText(block)) {
                count++;
            }
        }
        return count;
    }

    private String resolveOfficeExtension(String extension, File file) throws Exception {
        if (!isOfficeExtension(extension)) {
            return extension;
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            return resolveOfficeExtension(extension, input.readAllBytes());
        }
    }

    private String resolveOfficeExtension(String extension, byte[] bytes) throws Exception {
        if (!isOfficeExtension(extension) || bytes == null || bytes.length < 4 || !isZip(bytes)) {
            return extension;
        }
        String ooxmlExtension = detectOoxmlExtension(bytes);
        return StringUtils.hasText(ooxmlExtension) ? ooxmlExtension : extension;
    }

    private boolean isOfficeExtension(String extension) {
        return List.of(".doc", ".docx", ".ppt", ".pptx").contains(extension);
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private String detectOoxmlExtension(byte[] bytes) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int checked = 0;
            while ((entry = zip.getNextEntry()) != null && checked < 200) {
                checked++;
                String name = entry.getName();
                if (name == null) {
                    continue;
                }
                String normalized = name.replace('\\', '/');
                if (normalized.startsWith("word/")) {
                    return ".docx";
                }
                if (normalized.startsWith("ppt/")) {
                    return ".pptx";
                }
            }
        }
        return "";
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    private static final class ParseStats {
        private int pageCount;
        private int slideCount;
        private int paragraphCount;
    }
}
