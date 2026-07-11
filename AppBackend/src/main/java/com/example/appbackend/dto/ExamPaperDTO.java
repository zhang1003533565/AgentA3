package com.example.appbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ExamPaperDTO {

    private ExamPaperDTO() {
    }

    public enum PageSize { A3, A4, B4 }

    public enum Orientation { PORTRAIT, LANDSCAPE }

    public enum SelectionMode { RANDOM, MANUAL }

    public enum DownloadContent { PAPER, ANSWER }

    @Data
    public static class CreateRequest {
        @NotBlank
        @Size(max = 160)
        private String title;

        @Size(max = 200)
        private String subtitle;

        @Min(1)
        @Max(1440)
        private Integer durationMinutes;

        @Size(max = 2000)
        private String precautions;

        @Size(max = 300)
        private String headerInfo;

        @NotNull
        private PageSize pageSize;

        @NotNull
        private Orientation orientation;

        @Min(1)
        @Max(2)
        @NotNull
        private Integer columnsCount;

        @NotNull
        private SelectionMode selectionMode;

        @Valid
        @NotEmpty
        private List<SelectedQuestion> questions;
    }

    @Data
    public static class SelectedQuestion {
        @NotNull
        private Long questionId;

        @NotNull
        @DecimalMin("0.01")
        private BigDecimal score;

        @NotNull
        @Min(1)
        private Integer sortOrder;
    }

    @Data
    public static class RandomPreviewRequest {
        @Valid
        @NotEmpty
        private List<RandomRule> rules;
    }

    @Data
    public static class RandomRule {
        @NotBlank
        private String type;

        private String difficulty;

        @NotNull
        @Min(1)
        private Integer quantity;
    }

    @Data
    public static class PaperVO {
        private Long id;
        private String title;
        private String subtitle;
        private Integer durationMinutes;
        private String precautions;
        private String headerInfo;
        private PageSize pageSize;
        private Orientation orientation;
        private Integer columnsCount;
        private SelectionMode selectionMode;
        private Integer questionCount;
        private BigDecimal totalScore;
        private LocalDateTime createTime;
        private List<QuestionSnapshotVO> questions;
    }

    @Data
    public static class QuestionSnapshotVO {
        private Long id;
        private Long questionId;
        private Integer sortOrder;
        private Integer sectionOrder;
        private BigDecimal score;
        private String type;
        private String stem;
        private String bodyJson;
        private String answerJson;
        private String analysis;
        private String scoringJson;
    }

    @Data
    public static class PaperPageVO {
        private List<PaperVO> records;
        private long total;
        private int current;
        private int size;
        private int pages;
    }
}
