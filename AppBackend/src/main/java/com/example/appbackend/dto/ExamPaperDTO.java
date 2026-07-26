package com.example.appbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ExamPaperDTO {

    private ExamPaperDTO() {
    }

    public enum PageSize { A3, A4, B4 }

    public enum Orientation { PORTRAIT, LANDSCAPE }

    public enum PaperRenderMode { TEMPLATE, SIMPLE }

    public enum MarginPreset { NORMAL, NARROW, WIDE, BINDING, CUSTOM }

    public enum SelectionMode { RANDOM, MANUAL }

    public enum DownloadContent { PAPER, ANSWER }

    @Data
    public static class PaperLayoutConfig {
        @NotNull
        private PaperRenderMode renderMode = PaperRenderMode.TEMPLATE;
        @NotNull
        private PageSize pageSize = PageSize.A3;
        @NotNull
        private Orientation orientation = Orientation.LANDSCAPE;
        @NotNull
        private MarginPreset marginPreset = MarginPreset.BINDING;
        @Min(0) @Max(7200)
        private Integer customMarginTop;
        @Min(0) @Max(7200)
        private Integer customMarginRight;
        @Min(0) @Max(7200)
        private Integer customMarginBottom;
        @Min(0) @Max(7200)
        private Integer customMarginLeft;
        @NotNull @Min(1) @Max(2)
        private Integer columnsCount = 2;
        @NotNull @Min(0) @Max(2880)
        private Integer columnSpace = 425;
        @NotNull
        private Boolean hasBindingLine = true;
        @Size(max = 300)
        private String headerInfo = "煤矿___________    部门___________   岗位___________    姓名___________";
        @NotNull @Min(10) @Max(120)
        private Integer titleFontSize = 50;
        @NotNull @Min(10) @Max(72)
        private Integer subtitleFontSize = 24;
        @NotNull @Min(10) @Max(72)
        private Integer bodyFontSize = 21;
    }

    /** API layout contract shared by create and the preview endpoint added in Task 6. */
    @Data
    public static class PaperLayoutRequest {
        @NotNull
        private PaperRenderMode renderMode;
        @NotNull
        private PageSize pageSize;
        @NotNull
        private Orientation orientation;
        @NotNull
        private MarginPreset marginPreset;
        @Min(0) @Max(7200)
        private Integer customMarginTop;
        @Min(0) @Max(7200)
        private Integer customMarginRight;
        @Min(0) @Max(7200)
        private Integer customMarginBottom;
        @Min(0) @Max(7200)
        private Integer customMarginLeft;
        @NotNull @Min(1) @Max(2)
        private Integer columnsCount;
        @NotNull @Min(0) @Max(2880)
        private Integer columnSpace;
        @NotNull
        private Boolean hasBindingLine;
        @Size(max = 300)
        private String headerInfo;
        @NotNull @Min(10) @Max(120)
        private Integer titleFontSize;
        @NotNull @Min(10) @Max(72)
        private Integer subtitleFontSize;
        @NotNull @Min(10) @Max(72)
        private Integer bodyFontSize;
    }

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

        @Valid
        private PaperLayoutRequest layout;

        /** Transitional flat fields used by the current frontend until Task 7. */
        @Size(max = 300)
        private String headerInfo;
        private PageSize pageSize;
        private Orientation orientation;
        @Min(1) @Max(2)
        private Integer columnsCount;

        @NotNull
        private SelectionMode selectionMode;

        @Valid
        @NotEmpty
        private List<SelectedQuestion> questions;

        @Valid
        private Map<String, TypeScoreRuleRequest> typeScoreRules;

        @Valid
        private PreviewProof previewProof;
    }

    @Data
    public static class TypeScoreRuleRequest {
        @DecimalMin("0.01")
        private BigDecimal scorePerQuestion;

        @Size(max = 40)
        private String scoringRule;

        @Size(max = 160)
        private String customScoringRule;

        @Size(max = 200)
        private String scoringRuleText;
    }

    @Data
    public static class PreviewProof {
        @NotBlank private String token;
        @NotBlank private String configurationHash;
        @NotBlank private String questionHash;
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
        private PaperLayoutConfig layout;
        private SelectionMode selectionMode;
        private Integer questionCount;
        private BigDecimal totalScore;
        private Boolean published;
        private LocalDateTime publishTime;
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
