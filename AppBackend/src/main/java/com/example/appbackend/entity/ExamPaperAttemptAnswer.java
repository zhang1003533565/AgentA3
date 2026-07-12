package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_paper_attempt_answer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exam_attempt_answer_question", columnNames = {"attempt_id", "paper_question_id"})
}, indexes = {
        @Index(name = "idx_exam_attempt_answer_attempt", columnList = "attempt_id")
})
public class ExamPaperAttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "paper_question_id", nullable = false)
    private Long paperQuestionId;

    @Lob
    @Column(name = "answer_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL")
    private String answerJson;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "answered", nullable = false, columnDefinition = "BIT NOT NULL DEFAULT 0")
    private Boolean answered = false;

    @Column(name = "correct")
    private Boolean correct;

    @Column(name = "score", precision = 10, scale = 2)
    private BigDecimal score;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        updateTime = now;
        if (answered == null) answered = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
