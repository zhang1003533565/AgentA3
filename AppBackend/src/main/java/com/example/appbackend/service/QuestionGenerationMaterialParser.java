package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Locale;

@Service
public class QuestionGenerationMaterialParser {

    private final long maxFileBytes;
    private final int maxTextCharacters;
    private final long maxZipEntryBytes;
    private final long maxZipTotalBytes;
    private final int maxZipEntries;

    @Autowired
    public QuestionGenerationMaterialParser(
            @Value("${exam.question-generation.max-file-bytes:10485760}") long maxFileBytes,
            @Value("${exam.question-generation.max-text-characters:200000}") int maxTextCharacters,
            @Value("${exam.question-generation.max-zip-entry-bytes:16777216}") long maxZipEntryBytes,
            @Value("${exam.question-generation.max-zip-total-bytes:52428800}") long maxZipTotalBytes,
            @Value("${exam.question-generation.max-zip-entries:2000}") int maxZipEntries) {
        this.maxFileBytes = maxFileBytes;
        this.maxTextCharacters = maxTextCharacters;
        this.maxZipEntryBytes = maxZipEntryBytes;
        this.maxZipTotalBytes = maxZipTotalBytes;
        this.maxZipEntries = maxZipEntries;
    }

    public QuestionGenerationMaterialParser(long maxFileBytes, int maxTextCharacters) {
        this(maxFileBytes, maxTextCharacters, 16 * 1024 * 1024L, 50 * 1024 * 1024L, 2000);
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
        validateFileExtension(file.getOriginalFilename(), ".txt");
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
        validateFileExtension(file.getOriginalFilename(), ".docx");
        StringBuilder text = new StringBuilder();
        try {
            byte[] bytes = file.getBytes();
            preflightZip(bytes);
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    addNonBlank(text, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("DOCX 文件已损坏或无法读取", exception);
        }
        return parsed(text.toString(), file.getOriginalFilename(), sourceTitle(file.getOriginalFilename()));
    }

    private void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                addNonBlank(text, cell.getText());
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

    private void validateFileExtension(String filename, String expectedExtension) {
        String normalizedFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".doc")) {
            throw new IllegalArgumentException("不支持旧版 .doc 文件，请转换为 .docx");
        }
        if (!normalizedFilename.endsWith(expectedExtension)) {
            throw new IllegalArgumentException("材料文件扩展名必须为 " + expectedExtension);
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

    private void addNonBlank(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            int separator = text.isEmpty() ? 0 : 1;
            if ((long) text.length() + separator + value.length() > maxTextCharacters) {
                throw new IllegalArgumentException("材料内容不能超过 " + maxTextCharacters + " 个字符");
            }
            if (separator == 1) text.append('\n');
            text.append(value);
        }
    }

    private void preflightZip(byte[] bytes) throws IOException {
        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = new ByteArrayInputStream(bytes);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > maxZipEntries) throw new IllegalArgumentException("DOCX ZIP 条目数量超限");
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    total += read;
                    if (entryBytes > maxZipEntryBytes) throw new IllegalArgumentException("DOCX ZIP 条目解压大小超限");
                    if (total > maxZipTotalBytes) throw new IllegalArgumentException("DOCX ZIP 累计解压大小超限");
                }
            }
        }
        if (entries == 0) throw new IllegalArgumentException("DOCX 不是有效的 ZIP 包");
    }
}
