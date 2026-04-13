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

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    private final COSClient cosClient;

    @Value("${tencent.cos.bucket}")
    private String bucket;

    @Value("${tencent.cos.domain}")
    private String domain;

    @Value("${tencent.cos.upload-prefix}")
    private String uploadPrefix;

    public UploadController(COSClient cosClient) {
        this.cosClient = cosClient;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
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

        String objectKey = buildObjectKey(extension);
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

    private String buildObjectKey(String extension) {
        String normalizedPrefix = uploadPrefix == null ? "" : uploadPrefix.trim();
        normalizedPrefix = normalizedPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
        String datePath = LocalDate.now().toString();
        String filename = UUID.randomUUID() + extension;
        if (normalizedPrefix.isEmpty()) {
            return datePath + "/" + filename;
        }
        return normalizedPrefix + "/" + datePath + "/" + filename;
    }
}
