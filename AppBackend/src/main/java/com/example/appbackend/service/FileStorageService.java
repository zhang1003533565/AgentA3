package com.example.appbackend.service;

import com.example.appbackend.config.CourseMaterialProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 课程资料专用的文件存储服务（本地/腾讯云 COS 双模式）。
 * 逻辑参照 UploadController 但完全独立实现，不改动 UploadController；
 * 复用 application.yml 中已有的 tencent.cos.* 与 file.* 配置。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final COSClient cosClient;
    private final CourseMaterialProperties properties;

    @Value("${tencent.cos.bucket:}")
    private String bucket;

    @Value("${tencent.cos.domain:}")
    private String domain;

    @Value("${file.upload-dir:uploads}")
    private String localUploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

    public FileStorageService(COSClient cosClient, CourseMaterialProperties properties) {
        this.cosClient = cosClient;
        this.properties = properties;
    }

    /** 保存文件，返回可访问 URL。COS 未配置时自动降级为本地存储。 */
    public String store(MultipartFile file, Long courseId) throws IOException {
        String extension = extensionOf(file.getOriginalFilename());
        String objectKey = buildObjectKey(courseId, extension);
        if (cosEnabled()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (StringUtils.hasText(file.getContentType())) {
                metadata.setContentType(file.getContentType());
            }
            try (InputStream inputStream = file.getInputStream()) {
                cosClient.putObject(new PutObjectRequest(bucket, objectKey, inputStream, metadata));
            }
            return normalizeDomain(domain) + "/" + objectKey;
        }
        return saveLocally(file, objectKey);
    }

    /**
     * 物理删除文件（不可逆）。best-effort：失败仅记录日志，不影响软删除主流程。
     * 仅用于管理员二次确认后的可选强操作。
     */
    public void deletePhysical(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            if (cosEnabled() && fileUrl.startsWith(normalizeDomain(domain))) {
                String objectKey = fileUrl.substring(normalizeDomain(domain).length())
                        .replaceAll("^/+", "");
                cosClient.deleteObject(bucket, objectKey);
                return;
            }
            int idx = fileUrl.indexOf("/uploads/");
            if (idx >= 0) {
                String relative = fileUrl.substring(idx + "/uploads/".length());
                Path uploadRoot = Paths.get(localUploadDir).toAbsolutePath().normalize();
                Path target = uploadRoot.resolve(relative).normalize();
                if (target.startsWith(uploadRoot)) {
                    Files.deleteIfExists(target);
                }
            }
        } catch (Exception error) {
            log.warn("物理删除资料文件失败，已忽略：url={}, cause={}", fileUrl, error.getMessage());
        }
    }

    public String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean cosEnabled() {
        return StringUtils.hasText(bucket) && StringUtils.hasText(domain);
    }

    private String saveLocally(MultipartFile file, String objectKey) throws IOException {
        Path uploadRoot = Paths.get(localUploadDir).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(objectKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("上传路径不合法");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        String base = StringUtils.hasText(fileBaseUrl) ? fileBaseUrl.trim().replaceAll("/+$", "") : "";
        return base + "/uploads/" + objectKey.replace('\\', '/');
    }

    private String buildObjectKey(Long courseId, String extension) {
        String prefix = properties.getStoragePrefix() == null ? ""
                : properties.getStoragePrefix().trim().replaceAll("^/+", "").replaceAll("/+$", "");
        String datePath = LocalDate.now().toString();
        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        StringBuilder key = new StringBuilder();
        if (!prefix.isEmpty()) {
            key.append(prefix).append("/");
        }
        key.append(courseId).append("/").append(datePath).append("/").append(filename);
        return key.toString();
    }

    private String normalizeDomain(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
