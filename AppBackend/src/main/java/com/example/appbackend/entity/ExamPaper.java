package com.example.appbackend.entity;

import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.SelectionMode;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_paper", indexes = {
        @Index(name = "idx_exam_paper_created_by", columnList = "created_by"),
        @Index(name = "idx_exam_paper_create_time", columnList = "create_time")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 20)
    private SelectionMode selectionMode;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "total_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(nullable = false)
    private Integer status = 1;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
