package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.service.FileStorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    private static final Set<String> RESOURCE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif",
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".csv",
            ".txt", ".md", ".mmd", ".json", ".zip",
            ".mp3", ".wav", ".m4a", ".ogg", ".mp4", ".mov", ".webm"
    );
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(".mp3", ".wav", ".m4a", ".ogg");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".webm");
    private static final long MAX_RESOURCE_BYTES = 25L * 1024 * 1024;
    private static final int MAX_RESOURCE_COUNT = 8;
    private static final Map<String, String> UPLOAD_FOLDER_PREFIXES = new LinkedHashMap<>();

    static {
        UPLOAD_FOLDER_PREFIXES.put("map-buildings", "smart-campus/map-buildings");
        UPLOAD_FOLDER_PREFIXES.put("ai-resources", "smart-campus/ai-resources");
        UPLOAD_FOLDER_PREFIXES.put("canteen-stalls", "smart-campus/canteen-stalls");
        UPLOAD_FOLDER_PREFIXES.put("dishes", "smart-campus/dishes");
        UPLOAD_FOLDER_PREFIXES.put("public-facilities", "smart-campus/public-facilities");
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

    @Value("${file.upload-dir:uploads}")
    private String localUploadDir;

    @Value("${file.base-url:http://localhost:8080}")
    private String fileBaseUrl;

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
        String extension = extensionOf(file.getOriginalFilename());
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            return Result.badRequest("仅支持 jpg、jpeg、png、webp、gif 图片");
        }
        final String objectKey;
        try {
            objectKey = buildObjectKey(extension, folder);
        } catch (IllegalArgumentException error) {
            return Result.badRequest(error.getMessage());
        }
        try {
            return Result.success(Map.of("url", store(file, objectKey)));
        } catch (Exception error) {
            return Result.error("文件上传失败: " + error.getMessage());
        }
    }

    @PostMapping(value = "/resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadResource(@RequestParam("file") MultipartFile file) throws IOException {
        String validationError = validateResource(file);
        if (validationError != null) {
            return Result.badRequest(validationError);
        }
        return Result.success(storeResource(file));
    }

    @PostMapping(value = "/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<Map<String, Object>>> uploadResources(
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return Result.badRequest("请选择要上传的资源");
        }
        if (files.size() > MAX_RESOURCE_COUNT) {
            return Result.badRequest("单次最多上传 8 个资源");
        }
        for (MultipartFile file : files) {
            String validationError = validateResource(file);
            if (validationError != null) {
                return Result.badRequest(validationError);
            }
        }
        List<Map<String, Object>> resources = new ArrayList<>();
        for (MultipartFile file : files) {
            resources.add(storeResource(file));
        }
        return Result.success(resources);
    }

    private Map<String, Object> storeResource(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "resource");
        String extension = extensionOf(originalFilename);
        String objectKey = buildObjectKey(extension, "ai-resources");
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", UUID.randomUUID().toString());
        resource.put("name", originalFilename);
        resource.put("url", store(file, objectKey));
        resource.put("mimeType", StringUtils.hasText(file.getContentType())
                ? file.getContentType() : "application/octet-stream");
        resource.put("size", file.getSize());
        resource.put("type", resourceType(extension));
        return resource;
    }

    private String validateResource(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "资源文件不能为空";
        }
        if (file.getSize() > MAX_RESOURCE_BYTES) {
            return "单个资源不能超过 25MB";
        }
        if (!RESOURCE_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            return "不支持该资源格式";
        }
        return null;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private String resourceType(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) return "image";
        if (AUDIO_EXTENSIONS.contains(extension)) return "audio";
        if (VIDEO_EXTENSIONS.contains(extension)) return "video";
        return "document";
    }

    private String store(MultipartFile file, String objectKey) throws IOException {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(domain)) {
            return saveLocally(file, objectKey);
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        String ext = extensionOf(objectKey);
        metadata.setContentType(FileStorageService.resolveContentType(ext.startsWith(".") ? ext.substring(1) : ext));
        try (InputStream inputStream = file.getInputStream()) {
            cosClient.putObject(new PutObjectRequest(bucket, objectKey, inputStream, metadata));
        }
        String normalizedDomain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        return normalizedDomain + "/" + objectKey;
    }

    private String saveLocally(MultipartFile file, String objectKey) throws IOException {
        Path uploadRoot = Paths.get(localUploadDir).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(objectKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("上传路径不合法");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        String normalizedBaseUrl = StringUtils.hasText(fileBaseUrl)
                ? fileBaseUrl.trim().replaceAll("/+$", "")
                : "";
        return normalizedBaseUrl + "/uploads/" + objectKey.replace('\\', '/');
    }

    private String buildObjectKey(String extension, String folder) {
        String normalizedPrefix = resolveUploadPrefix(folder);
        String datePath = LocalDate.now().toString();
        String filename = UUID.randomUUID() + extension;
        return normalizedPrefix.isEmpty()
                ? datePath + "/" + filename
                : normalizedPrefix + "/" + datePath + "/" + filename;
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
        return prefix == null ? "" : prefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
