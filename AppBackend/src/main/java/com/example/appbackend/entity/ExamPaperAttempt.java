package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_paper_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exam_attempt_number", columnNames = {"paper_id", "user_id", "attempt_no"}),
        @UniqueConstraint(name = "uk_exam_attempt_active", columnNames = {"paper_id", "user_id", "active_marker"})
}, indexes = {
        @Index(name = "idx_exam_attempt_user_paper_status_started", columnList = "user_id,paper_id,status,started_at")
})
public class ExamPaperAttempt {

    public enum Status { IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    /** Non-null only while active; MySQL unique indexes allow multiple NULL values. */
    @Column(name = "active_marker", columnDefinition = "TINYINT DEFAULT NULL")
    private Integer activeMarker = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "objective_score", nullable = false, precision = 10, scale = 2,
            columnDefinition = "DECIMAL(10,2) NOT NULL DEFAULT 0")
    private BigDecimal objectiveScore = BigDecimal.ZERO;

    @Column(name = "objective_total_score", nullable = false, precision = 10, scale = 2,
            columnDefinition = "DECIMAL(10,2) NOT NULL DEFAULT 0")
    private BigDecimal objectiveTotalScore = BigDecimal.ZERO;

    @Column(name = "answered_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer answeredCount = 0;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    /** Ordered paper-question ids selected once for this attempt. */
    @Lob
    @Column(name = "selected_question_ids_json", columnDefinition = "LONGTEXT")
    private String selectedQuestionIdsJson;

    /** Immutable response snapshot created with the first successful submission. */
    @Lob
    @Column(name = "learning_update_json", columnDefinition = "LONGTEXT COMMENT '考试学习闭环结果快照'")
    private String learningUpdateJson;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        updateTime = now;
        if (status == null) status = Status.IN_PROGRESS;
        activeMarker = status == Status.IN_PROGRESS ? 1 : null;
        if (objectiveScore == null) objectiveScore = BigDecimal.ZERO;
        if (objectiveTotalScore == null) objectiveTotalScore = BigDecimal.ZERO;
        if (answeredCount == null) answeredCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        activeMarker = status == Status.IN_PROGRESS ? 1 : null;
    }
}
