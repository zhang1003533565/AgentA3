package com.example.appbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程资料池与精细进度相关的传输对象。
 */
public class MaterialDTO {

    /** 资料池条目视图（管理端）。 */
    @Data
    public static class MaterialView {
        private Long id;
        private Long courseId;
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private String fileType;
        private String mimeType;
        private Integer durationSeconds;
        private Boolean deleted;
        private String uploadBatchId;
        private LocalDateTime createdAt;
    }

    /** 文件夹上传结果。 */
    @Data
    public static class FolderUploadResult {
        private String uploadBatchId;
        private int uploadedCount;
        private long uploadedBytes;
        private long batchTotalBytes;
        private List<MaterialView> materials;
    }

    /** 资料引用检查结果。 */
    @Data
    public static class ReferenceCheck {
        private Long materialId;
        private boolean referenced;
        private List<String> chapterTitles;
    }

    /** 章节资料绑定请求：将资料 ID 数组写入章节 material_ids（保留传入顺序）。 */
    @Data
    public static class ChapterBindRequest {
        @NotNull(message = "courseId 不能为空")
        private Long courseId;

        /** 选中的资料 ID 列表，顺序即章节内展示顺序；允许为空表示清空。 */
        private List<Long> materialIds;
    }

    /** 章节附加资料绑定请求：将资料 ID 数组写入章节 additional_material_ids。 */
    @Data
    public static class AdditionalChapterBindRequest {
        @NotNull(message = "courseId 不能为空")
        private Long courseId;

        /** 选中的附加下载资料 ID 列表，顺序即章节内展示顺序；允许为空表示清空。 */
        private List<Long> materialIds;
    }

    /** 章节 Word 文本资料绑定请求：将资料 ID 数组写入章节 word_material_ids。 */
    @Data
    public static class WordChapterBindRequest {
        @NotNull(message = "courseId 不能为空")
        private Long courseId;

        /** 选中的 Word 资料 ID 列表，顺序即章节内展示顺序；允许为空表示清空。 */
        private List<Long> materialIds;
    }

    /** 进度上报请求。 */
    @Data
    public static class ProgressReportRequest {
        @NotNull(message = "materialId 不能为空")
        private Long materialId;

        @NotNull(message = "watchSeconds 不能为空")
        @Min(value = 0, message = "watchSeconds 不能为负")
        private Integer watchSeconds;
    }

    /** 单个资料的进度视图（学生端）。 */
    @Data
    public static class MaterialProgressView {
        private Long materialId;
        private String name;
        private String url;
        private String type;
        private Integer durationSeconds;
        private Integer watchSeconds;
        private Integer status;
    }

    /** 章节级进度视图。 */
    @Data
    public static class ChapterProgressView {
        private Long chapterId;
        private String title;
        private int totalCount;
        private int completedCount;
        private int percent;
        private List<MaterialProgressView> materials;
    }

    /** 课程级进度视图。 */
    @Data
    public static class CourseProgressView {
        private Long courseId;
        private int percent;
        private int totalCount;
        private int completedCount;
        private List<ChapterProgressView> chapters;
    }
}
