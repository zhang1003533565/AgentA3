package com.example.appbackend.entity;

import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode;
import com.example.appbackend.dto.ExamPaperDTO.SelectionMode;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_paper", indexes = {
        @Index(name = "idx_exam_paper_created_by", columnList = "created_by"),
        @Index(name = "idx_exam_paper_create_time", columnList = "create_time"),
        @Index(name = "idx_exam_paper_creator_status_time", columnList = "created_by,status,create_time")
})
public class ExamPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(length = 2000)
    private String precautions;

    @Column(name = "header_info", length = 300)
    private String headerInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_size", nullable = false, length = 10)
    private PageSize pageSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Orientation orientation;

    @Column(name = "columns_count", nullable = false)
    private Integer columnsCount;

    /** Defaults in the DDL preserve pre-migration papers as the legacy renderer. */
    @Enumerated(EnumType.STRING)
    @Column(name = "render_mode", nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'SIMPLE'")
    private PaperRenderMode renderMode = PaperRenderMode.SIMPLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "margin_preset", nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'NORMAL'")
    private MarginPreset marginPreset = MarginPreset.NORMAL;

    @Column(name = "custom_margin_top")
    private Integer customMarginTop;

    @Column(name = "custom_margin_right")
    private Integer customMarginRight;

    @Column(name = "custom_margin_bottom")
    private Integer customMarginBottom;

    @Column(name = "custom_margin_left")
    private Integer customMarginLeft;

    @Column(name = "column_space", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 425")
    private Integer columnSpace = 425;

    @Column(name = "has_binding_line", nullable = false,
            columnDefinition = "BIT NOT NULL DEFAULT 0")
    private Boolean hasBindingLine = false;

    @Column(name = "title_font_size", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 50")
    private Integer titleFontSize = 50;

    @Column(name = "subtitle_font_size", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 24")
    private Integer subtitleFontSize = 24;

    @Column(name = "body_font_size", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 21")
    private Integer bodyFontSize = 21;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 20)
    private SelectionMode selectionMode;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "total_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "published", nullable = false, columnDefinition = "BIT NOT NULL DEFAULT 0")
    private Boolean published = false;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
        if (published == null) {
            published = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    @PostLoad
    protected void applyHistoricalLayoutDefaults() {
        if (renderMode == null) renderMode = PaperRenderMode.SIMPLE;
        if (marginPreset == null) marginPreset = MarginPreset.NORMAL;
        if (columnSpace == null) columnSpace = 425;
        if (hasBindingLine == null) hasBindingLine = false;
        if (titleFontSize == null) titleFontSize = 50;
        if (subtitleFontSize == null) subtitleFontSize = 24;
        if (bodyFontSize == null) bodyFontSize = 21;
        if (published == null) published = false;
    }
}
