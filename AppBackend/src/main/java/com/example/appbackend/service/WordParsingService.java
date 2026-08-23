package com.example.appbackend.service;

import com.example.appbackend.dto.WordContentDTO;
import com.example.appbackend.entity.CampusCourseMaterial;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Word 文档解析服务：将 .doc/.docx 文件解析为纯文本，支持分页返回。
 * 仅处理本地存储的文件（URL 为 fileBaseUrl 前缀的文件）；COS 文件暂不支持解析。
 */
@Service
public class WordParsingService {

    private static final Logger log = LoggerFactory.getLogger(WordParsingService.class);

    @Value("${file.upload-dir:uploads}")
    private String localUploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

    /**
     * 解析 Word 文件并返回指定页的内容。
     *
     * @param material 资料实体（含 fileUrl）
     * @param page     页码，从 1 开始
     * @param pageSize 每页字符数
     * @return 分页响应
     */
    public WordContentDTO.PageResponse parsePage(
            CampusCourseMaterial material, int page, int pageSize
    ) {
        String fullText = extractText(material);
        if (fullText == null || fullText.isEmpty()) {
            WordContentDTO.PageResponse resp = new WordContentDTO.PageResponse();
            resp.setMaterialId(material.getId());
            resp.setFileName(material.getFileName());
            resp.setTotalPages(1);
            resp.setCurrentPage(1);
            resp.setPageSize(pageSize);
            resp.setContent("");
            return resp;
        }

        int totalChars = fullText.length();
        int totalPages = (int) Math.ceil((double) totalChars / pageSize);
        if (totalPages < 1) totalPages = 1;

        int currentPage = Math.max(1, Math.min(page, totalPages));
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, totalChars);
        String pageContent = fullText.substring(start, end).trim();

        WordContentDTO.PageResponse resp = new WordContentDTO.PageResponse();
        resp.setMaterialId(material.getId());
        resp.setFileName(material.getFileName());
        resp.setTotalPages(totalPages);
        resp.setCurrentPage(currentPage);
        resp.setPageSize(pageSize);
        resp.setContent(pageContent);
        return resp;
    }

    /**
     * 从资料文件提取全部文本内容。
     */
    private String extractText(CampusCourseMaterial material) {
        Path filePath = resolveLocalPath(material.getFileUrl());
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("Word 文件路径不可访问：url={}, resolved={}", material.getFileUrl(), filePath);
            return "";
        }

        String fileName = material.getFileName();
        String ext = extensionOf(fileName).toLowerCase();

        try {
            if ("docx".equals(ext)) {
                return extractDocx(filePath);
            } else if ("doc".equals(ext)) {
                return extractDoc(filePath);
            }
            log.warn("不支持的文件类型：{}", ext);
            return "";
        } catch (Exception e) {
            log.error("Word 文件解析失败：path={}", filePath, e);
            return "";
        }
    }

    private String extractDocx(Path filePath) throws IOException {
        try (InputStream is = new FileInputStream(filePath.toFile());
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            return text == null ? "" : text;
        }
    }

    private String extractDoc(Path filePath) throws IOException {
        try (InputStream is = new FileInputStream(filePath.toFile());
             HWPFDocument doc = new HWPFDocument(is)) {
            String text = doc.getDocumentText();
            return text == null ? "" : text;
        }
    }

    /**
     * 将存储 URL 解析为本地文件路径。
     * 本地文件 URL 格式：{fileBaseUrl}/uploads/{objectKey}
     * 本地路径：{uploadDir}/{objectKey}
     */
    private Path resolveLocalPath(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String normalizedBase = fileBaseUrl.trim().replaceAll("/+$", "");
        if (!fileUrl.startsWith(normalizedBase)) {
            log.warn("文件 URL 不是本地存储：{}", fileUrl);
            return null;
        }
        String relative = fileUrl.substring(normalizedBase.length()).replaceAll("^/+", "");
        // relative 形如 "uploads/smart-campus/course-materials/1/2026-08-01/uuid.docx"
        // uploadDir 形如 "uploads"
        String uploadDir = localUploadDir.trim().replaceAll("/+$", "");
        if (uploadDir.isEmpty() || relative.startsWith(uploadDir + "/")) {
            // relative 已包含 uploadDir 前缀，直接用
            return Paths.get(relative).toAbsolutePath().normalize();
        }
        // 从 relative 中提取 uploadDir 之后的部分
        return Paths.get(relative).toAbsolutePath().normalize();
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
