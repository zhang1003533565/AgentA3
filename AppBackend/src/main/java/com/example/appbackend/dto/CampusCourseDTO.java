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
        private String description;
        private String semester;
        @Min(1)
        @Max(10000)
        private Integer estimatedHours;
        private String audienceType = "ALL";
        private String audienceValues;
        private Integer sortOrder = 0;
    }

    @Data
    public static class ChapterSaveRequest {
        @NotBlank
        private String title;
        private String summary;
        private String content;
        private String resourceType;
        private String resourceUrl;
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
    public static class CourseSummary {
        private Long id;
        private String name;
        private String bookTitle;
        private String coverUrl;
        private String description;
        private String semester;
        private Integer estimatedHours;
        private Long ownerId;
        private String ownerName;
        private String ownerType;
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
        private String resourceType;
        private String resourceUrl;
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
