package com.example.appbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class CampusCourseDTO {
    private CampusCourseDTO() {}

    @Data
    public static class SaveRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String bookTitle;
        private String coverUrl;
        private String displayImageUrl;
        private String description;
        @NotBlank
        private String courseType;
        private List<String> customCourseTypes;
        private Integer sortOrder = 0;
    }

    @Data
    public static class ChapterSaveRequest {
        @NotBlank
        private String title;
        private String summary;
        private String content;
        @Min(1)
        @Max(100000)
        private Integer estimatedMinutes;
        private Boolean required = true;
        private Integer sortOrder = 0;
    }

    @Data
    public static class ExamLinkRequest {
        @NotNull
        private Long paperId;
        private String chapterScope;
        private LocalDateTime deadline;
        private Integer sortOrder = 0;
    }

    @Data
    public static class ProgressRequest {
        @NotNull
        private Boolean completed;
    }

    @Data
    public static class CourseTypeSaveRequest {
        /** 仅需类型名称，typeCode 由后端自动生成，id 由数据库自增分配 */
        @NotBlank
        private String typeName;
        private Integer sortOrder = 0;
    }

    @Data
    public static class CourseTypeView {
        private Long id;
        private String typeCode;
        private String typeName;
        private String category;
        private Integer sortOrder;
    }

    @Data
    public static class CourseSummary {
        private Long id;
        private String name;
        private String bookTitle;
        private String coverUrl;
        private String displayImageUrl;
        private String description;
        private String semester;
        private Integer estimatedHours;
        private Long ownerId;
        private String ownerName;
        private String ownerType;
        private String courseType;
        private List<String> customCourseTypes = new ArrayList<>();
        /** 与 customCourseTypes 一一对应的类型名称，供前端直接渲染，不依赖类型字典接口 */
        private List<String> customCourseTypeNames = new ArrayList<>();
        private String audienceType;
        private String audienceValues;
        private String publishStatus;
        private Integer sortOrder;
        private long chapterCount;
        private long examCount;
        private int progressPercent;
        private String currentChapterTitle;
        private LocalDateTime publishTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ChapterView {
        private Long id;
        private Long courseId;
        private String title;
        private String summary;
        private String content;
        private Integer estimatedMinutes;
        private Boolean required;
        private Integer sortOrder;
        private Boolean completed;
        private LocalDateTime completedTime;
    }

    @Data
    public static class ExamView {
        private Long id;
        private Long paperId;
        private String title;
        private String subtitle;
        private String chapterScope;
        private Integer questionCount;
        private Integer durationMinutes;
        private Object totalScore;
        private Boolean published;
        private LocalDateTime deadline;
        private Integer sortOrder;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CourseDetail extends CourseSummary {
        private List<ChapterView> chapters = new ArrayList<>();
        private List<ExamView> exams = new ArrayList<>();
    }
}
