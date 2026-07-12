package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class QuestionGenerationMaterialParser {

    private final long maxFileBytes;
    private final int maxTextCharacters;

    public QuestionGenerationMaterialParser(
            @Value("${exam.question-generation.max-file-bytes:10485760}") long maxFileBytes,
            @Value("${exam.question-generation.max-text-characters:200000}") int maxTextCharacters) {
        this.maxFileBytes = maxFileBytes;
        this.maxTextCharacters = maxTextCharacters;
    }

    public ParsedMaterial parse(String sourceType, MultipartFile file, String text) {
        String normalizedType = sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedType) {
            case "text" -> parsed(text, null, "文本材料");
            case "txt" -> parseTxt(requireFile(file));
            case "docx" -> parseDocx(requireFile(file));
            default -> throw new IllegalArgumentException("不支持的材料类型: " + sourceType);
        };
    }

    private ParsedMaterial parseTxt(MultipartFile file) {
        validateFile(file);
        rejectLegacyDoc(file.getOriginalFilename());
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String content = decoder.decode(ByteBuffer.wrap(file.getBytes())).toString();
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            return parsed(content, file.getOriginalFilename(), sourceTitle(file.getOriginalFilename()));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("TXT 文件不是合法的 UTF-8 编码", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 TXT 文件", exception);
        }
    }

    private ParsedMaterial parseDocx(MultipartFile file) {
        validateFile(file);
        rejectLegacyDoc(file.getOriginalFilename());
        List<String> parts = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.getBytes()))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    addNonBlank(parts, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    appendTable(parts, table);
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("DOCX 文件已损坏或无法读取", exception);
        }
        return parsed(String.join("\n", parts), file.getOriginalFilename(), sourceTitle(file.getOriginalFilename()));
    }

    private void appendTable(List<String> parts, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                addNonBlank(parts, cell.getText());
            }
        }
    }

    private ParsedMaterial parsed(String content, String filename, String title) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("材料内容不能为空");
        }
        if (content.length() > maxTextCharacters) {
            throw new IllegalArgumentException("材料内容不能超过 " + maxTextCharacters + " 个字符");
        }
        return new ParsedMaterial(content, filename, title);
    }

    private MultipartFile requireFile(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("材料文件不能为空");
        }
        return file;
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > maxFileBytes) {
            throw new IllegalArgumentException("材料文件不能超过 " + maxFileBytes + " 字节");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("材料内容不能为空");
        }
    }

    private void rejectLegacyDoc(String filename) {
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".doc")) {
            throw new IllegalArgumentException("不支持旧版 .doc 文件，请转换为 .docx");
        }
    }

    private String sourceTitle(String filename) {
        if (filename == null || filename.isBlank()) {
            return "未命名材料";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = filename.substring(slash + 1);
        int extension = basename.lastIndexOf('.');
        return extension > 0 ? basename.substring(0, extension) : basename;
    }

    private void addNonBlank(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }
}
