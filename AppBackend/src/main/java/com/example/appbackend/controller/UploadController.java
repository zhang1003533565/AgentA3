package com.example.appbackend.controller;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.example.appbackend.entity.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    private static final Map<String, String> UPLOAD_FOLDER_PREFIXES = new LinkedHashMap<>();

    static {
        UPLOAD_FOLDER_PREFIXES.put("map-buildings", "smart-campus/map-buildings");
    }

    private final COSClient cosClient;

    @Value("${tencent.cos.bucket}")
    private String bucket;

    @Value("${tencent.cos.domain}")
    private String domain;

    @Value("${tencent.cos.upload-prefix}")
    private String uploadPrefix;

    @Value("${tencent.cos.map-buildings-prefix:smart-campus/map-buildings}")
    private String mapBuildingsPrefix;

    public UploadController(COSClient cosClient) {
        this.cosClient = cosClient;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder,
            HttpServletRequest request) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.badRequest("请选择图片文件");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.badRequest("仅支持 jpg、jpeg、png、webp、gif 图片");
        }

        String objectKey;
        try {
            objectKey = buildObjectKey(extension, folder);
        } catch (IllegalArgumentException error) {
            return Result.badRequest(error.getMessage());
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, objectKey, inputStream, metadata);
            cosClient.putObject(putObjectRequest);
        } catch (Exception error) {
            return Result.error("腾讯云 COS 上传失败: " + error.getMessage());
        }

        String normalizedDomain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        String fileUrl = normalizedDomain + "/" + objectKey;

        return Result.success(Map.of("url", fileUrl));
    }

    private String buildObjectKey(String extension, String folder) {
        String normalizedPrefix = resolveUploadPrefix(folder);
        String datePath = LocalDate.now().toString();
        String filename = UUID.randomUUID() + extension;
        if (normalizedPrefix.isEmpty()) {
            return datePath + "/" + filename;
        }
        return normalizedPrefix + "/" + datePath + "/" + filename;
    }

    private String resolveUploadPrefix(String folder) {
        if (StringUtils.hasText(folder)) {
            String key = folder.trim().toLowerCase();
            if ("map-buildings".equals(key)) {
                return normalizePrefix(mapBuildingsPrefix);
            }
            if (!UPLOAD_FOLDER_PREFIXES.containsKey(key)) {
                throw new IllegalArgumentException("不支持的上传目录: " + folder);
            }
            return normalizePrefix(UPLOAD_FOLDER_PREFIXES.get(key));
        }
        return normalizePrefix(uploadPrefix);
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        return prefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
