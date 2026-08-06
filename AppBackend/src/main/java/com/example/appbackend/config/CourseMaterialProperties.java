package com.example.appbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程资料上传相关的可配置项，绑定 application.yml 中的 course-material.*。
 * 文件夹大小上限与扩展名白名单外置配置，方便后期调整。
 */
@Component
@ConfigurationProperties(prefix = "course-material")
public class CourseMaterialProperties {

    /** 同一文件夹（同一 upload_batch_id）累计字节上限，默认 2GB。 */
    private long maxFolderBytes = 2L * 1024 * 1024 * 1024;

    /** 允许上传的扩展名白名单（逗号分隔，不含点，小写）。 */
    private String allowedExtensions =
            "mp4,avi,pdf,ppt,pptx,doc,docx,xls,xlsx,png,jpg,jpeg,gif,webp,mp3,txt";

    /** COS/本地存储的对象键前缀。 */
    private String storagePrefix = "smart-campus/course-materials";

    public long getMaxFolderBytes() {
        return maxFolderBytes;
    }

    public void setMaxFolderBytes(long maxFolderBytes) {
        this.maxFolderBytes = maxFolderBytes;
    }

    public String getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public String getStoragePrefix() {
        return storagePrefix;
    }

    public void setStoragePrefix(String storagePrefix) {
        this.storagePrefix = storagePrefix;
    }

    /** 解析为小写、去点、去空的扩展名集合。 */
    public Set<String> allowedExtensionSet() {
        if (allowedExtensions == null || allowedExtensions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.startsWith(".") ? item.substring(1) : item)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
