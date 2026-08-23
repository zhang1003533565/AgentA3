package com.example.appbackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.appbackend.entity.DocumentConvertTask;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档格式转换核心服务。
 * 当前阶段仅提供源文件存取与转换占位方法，转换能力待后续阶段接入 Python 服务。
 */
@Service
public class DocumentConvertServiceImpl {

    public static final String CONVERT_TYPE_PDF_TO_DOCX = "pdf_to_docx";
    public static final String CONVERT_TYPE_PPT_TO_DOCX = "ppt_to_docx";
    public static final String CONVERT_TYPE_PDF_TO_PPT = "pdf_to_ppt";
    public static final String CONVERT_TYPE_PPT_TO_PDF = "ppt_to_pdf";
    public static final String CONVERT_TYPE_DOCX_TO_PDF = "docx_to_pdf";
    public static final String CONVERT_TYPE_DOCX_TO_PPT = "docx_to_ppt";

    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    private static final String SOURCE_PREFIX = "convert-src";
    private static final String RESULT_PREFIX = "convert-result";

    private static final Set<String> SUPPORTED_CONVERT_TYPES = Set.of(
            CONVERT_TYPE_PDF_TO_DOCX,
            CONVERT_TYPE_PPT_TO_DOCX,
            CONVERT_TYPE_PDF_TO_PPT,
            CONVERT_TYPE_PPT_TO_PDF,
            CONVERT_TYPE_DOCX_TO_PDF,
            CONVERT_TYPE_DOCX_TO_PPT
    );

    private static final Map<String, Set<String>> CONVERT_TYPE_EXTENSIONS = Map.of(
            CONVERT_TYPE_PDF_TO_DOCX, Set.of(".pdf"),
            CONVERT_TYPE_PPT_TO_DOCX, Set.of(".pptx"),
            CONVERT_TYPE_PDF_TO_PPT, Set.of(".pdf"),
            CONVERT_TYPE_PPT_TO_PDF, Set.of(".ppt", ".pptx"),
            CONVERT_TYPE_DOCX_TO_PDF, Set.of(".docx"),
            CONVERT_TYPE_DOCX_TO_PPT, Set.of(".docx")
    );

    private final String uploadDir;
    private final String fileBaseUrl;
    private final PythonAiProxyService pythonAiProxyService;
    private final ObjectMapper objectMapper;

    public DocumentConvertServiceImpl(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${file.base-url:http://localhost:8080}") String fileBaseUrl,
            PythonAiProxyService pythonAiProxyService,
            ObjectMapper objectMapper) {
        this.uploadDir = uploadDir;
        this.fileBaseUrl = fileBaseUrl;
        this.pythonAiProxyService = pythonAiProxyService;
        this.objectMapper = objectMapper;
    }

    public record SourceFileInfo(String fileName, String url, String storageKey, long size) {
    }

    public record ResultFileInfo(String fileName, String url, String storageKey, long size) {
    }

    public boolean isSupportedConvertType(String convertType) {
        return convertType != null && SUPPORTED_CONVERT_TYPES.contains(convertType);
    }

    public Set<String> expectedExtensions(String convertType) {
        return CONVERT_TYPE_EXTENSIONS.get(convertType);
    }

    public SourceFileInfo saveSourceFile(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document");
        String extension = extensionOf(originalFilename);
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        String datePath = LocalDate.now().toString();
        String filename = UUID.randomUUID() + extension;
        String relativeKey = SOURCE_PREFIX + "/" + datePath + "/" + filename;
        Path target = uploadRoot.resolve(relativeKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("上传路径不合法");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        String normalizedBaseUrl = StringUtils.hasText(fileBaseUrl)
                ? fileBaseUrl.trim().replaceAll("/+$", "")
                : "";
        return new SourceFileInfo(
                originalFilename,
                normalizedBaseUrl + "/uploads/" + relativeKey.replace('\\', '/'),
                relativeKey,
                file.getSize()
        );
    }

    public byte[] loadFile(String storageKey) throws IOException {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(storageKey).normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new IOException("存储文件不存在或路径不合法");
        }
        return Files.readAllBytes(target);
    }

    /**
     * 删除存储文件。路径需位于上传根目录内；文件不存在或删除失败不抛错，由调用方继续删除数据库记录。
     */
    public void deleteFile(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        try {
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = uploadRoot.resolve(storageKey).normalize();
            if (!target.startsWith(uploadRoot)) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException error) {
            // 文件删除失败不阻断数据库删除
        }
    }

    /**
     * 转换执行核心。
     * 根据任务转换类型复用 Python AI 服务现有转换能力：
     * - pdf_to_docx: PythonAiProxyService.convertPdf(file, "docx", authorization)
     * - ppt_to_docx: PythonAiProxyService.convertPpt(file, authorization)
     * 转换结果以 Base64 返回，解码后保存到 uploads/convert-result/ 并回填任务结果字段。
     */
    public void executeConvertTask(DocumentConvertTask task, String authorization) {
        if (task == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "转换任务不存在");
        }
        if (!StringUtils.hasText(task.getSourceStorageKey())) {
            throw new BusinessException(Result.ERROR_CODE, "源文件存储信息缺失");
        }

        final byte[] sourceBytes;
        try {
            sourceBytes = loadFile(task.getSourceStorageKey());
        } catch (IOException error) {
            throw new BusinessException(Result.ERROR_CODE, "源文件读取失败: " + error.getMessage());
        }
        StoredMultipartFile sourceFile = new StoredMultipartFile(
                task.getSourceFileName(), sourceBytes, "application/octet-stream");

        long startMillis = System.currentTimeMillis();
        Object rawResponse = invokePythonConvert(task, sourceFile, authorization);
        long convertMs = System.currentTimeMillis() - startMillis;

        if (!(rawResponse instanceof Map<?, ?> response)) {
            throw new BusinessException(Result.ERROR_CODE, "Python 转换服务返回格式异常");
        }
        String contentBase64 = textValue(response.get("contentBase64"));
        if (!StringUtils.hasText(contentBase64)) {
            throw new BusinessException(Result.ERROR_CODE, "Python 转换未返回结果文件");
        }
        final byte[] resultBytes;
        try {
            resultBytes = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(Result.ERROR_CODE, "转换结果解码失败");
        }
        if (resultBytes.length == 0) {
            throw new BusinessException(Result.ERROR_CODE, "转换结果为空");
        }

        String responseFileName = textValue(response.get("fileName"));
        String resultFileName = StringUtils.hasText(responseFileName)
                ? responseFileName
                : task.getSourceFileName();
        final ResultFileInfo resultFile;
        try {
            resultFile = saveResultFile(resultBytes, resultFileName);
        } catch (IOException error) {
            throw new BusinessException(Result.ERROR_CODE, "结果文件保存失败: " + error.getMessage());
        }

        task.setResultFileName(resultFile.fileName());
        task.setResultFileUrl(resultFile.url());
        task.setResultFileSize(resultFile.size());
        task.setResultStorageKey(resultFile.storageKey());
        task.setResultExtraJson(buildExtraJson(response, convertMs));
        task.setProgress(90);
        task.setMessage("正在保存转换结果");
    }

    private Object invokePythonConvert(DocumentConvertTask task, MultipartFile sourceFile, String authorization) {
        if (CONVERT_TYPE_PDF_TO_DOCX.equals(task.getConvertType())) {
            String convertMode = task.getConvertMode();
            if (!StringUtils.hasText(convertMode)) {
                convertMode = "reflow";
            }
            return pythonAiProxyService.convertPdf(sourceFile, "docx", authorization, convertMode);
        }
        if (CONVERT_TYPE_PPT_TO_DOCX.equals(task.getConvertType())) {
            String convertMode = task.getConvertMode();
            if (!StringUtils.hasText(convertMode)) {
                convertMode = "reflow";
            }
            return pythonAiProxyService.convertPpt(sourceFile, authorization, convertMode);
        }
        if (CONVERT_TYPE_PDF_TO_PPT.equals(task.getConvertType())) {
            String convertMode = task.getConvertMode();
            if (!StringUtils.hasText(convertMode)) {
                convertMode = "image";
            }
            return pythonAiProxyService.convertPdfToPpt(sourceFile, authorization, convertMode);
        }
        if (CONVERT_TYPE_PPT_TO_PDF.equals(task.getConvertType())) {
            return pythonAiProxyService.convertPptToPdf(sourceFile, authorization);
        }
        if (CONVERT_TYPE_DOCX_TO_PDF.equals(task.getConvertType())) {
            return pythonAiProxyService.convertDocxToPdf(sourceFile, authorization);
        }
        if (CONVERT_TYPE_DOCX_TO_PPT.equals(task.getConvertType())) {
            String convertMode = task.getConvertMode();
            if (!StringUtils.hasText(convertMode)) {
                convertMode = "smart";
            }
            return pythonAiProxyService.convertDocxToPpt(sourceFile, authorization, convertMode);
        }
        throw new BusinessException(Result.BAD_REQUEST_CODE, "不支持的转换类型: " + task.getConvertType());
    }

    public ResultFileInfo saveResultFile(byte[] bytes, String originalFileName) throws IOException {
        String extension = extensionOf(originalFileName);
        if (!StringUtils.hasText(extension)) {
            extension = ".bin";
        }
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        String datePath = LocalDate.now().toString();
        String filename = UUID.randomUUID() + extension;
        String relativeKey = RESULT_PREFIX + "/" + datePath + "/" + filename;
        Path target = uploadRoot.resolve(relativeKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("结果保存路径不合法");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        String normalizedBaseUrl = StringUtils.hasText(fileBaseUrl)
                ? fileBaseUrl.trim().replaceAll("/+$", "")
                : "";
        return new ResultFileInfo(
                originalFileName,
                normalizedBaseUrl + "/uploads/" + relativeKey.replace('\\', '/'),
                relativeKey,
                bytes.length
        );
    }

    private String buildExtraJson(Map<?, ?> response, long convertMs) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pageCount", numberValue(response.get("pageCount")));
        extra.put("imageCount", numberValue(response.get("imageCount")));
        extra.put("convertMs", convertMs);
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (JsonProcessingException error) {
            return "{\"convertMs\":" + convertMs + "}";
        }
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 将已落盘的源文件字节包装为 MultipartFile，供 Python 转换代理方法复用。
     * 仅作为本模块内部适配使用，不对外暴露。
     */
    private static final class StoredMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        StoredMultipartFile(String originalFilename, byte[] content, String contentType) {
            this.name = "file";
            this.originalFilename = originalFilename == null ? "file" : originalFilename;
            this.content = content == null ? new byte[0] : content;
            this.contentType = contentType == null ? "application/octet-stream" : contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            Files.write(dest, content);
        }
    }
}
