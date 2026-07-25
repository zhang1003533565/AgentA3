package com.example.appbackend.entity;

import com.example.appbackend.domain.LearningStatuses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_knowledge_mastery", uniqueConstraints = {
        @UniqueConstraint(name = "uk_learning_mastery_user_course_point",
                columnNames = {"user_id", "course_key", "knowledge_point_key"})
}, indexes = {
        @Index(name = "idx_learning_mastery_user_course", columnList = "user_id,course_key"),
        @Index(name = "idx_learning_mastery_review", columnList = "status,next_review_at")
})
public class LearningKnowledgeMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_key", nullable = false, length = 40)
    private String courseKey;

    @Column(name = "knowledge_point_key", nullable = false, length = 160)
    private String knowledgePointKey;

    @Column(name = "knowledge_point_name", length = 200)
    private String knowledgePointName;

    @Column(name = "last_attempt_id")
    private Long lastAttemptId;

    @Lob
    @Column(name = "applied_attempt_ids_json", nullable = false,
            columnDefinition = "LONGTEXT NOT NULL")
    private String appliedAttemptIdsJson = "[]";

    @Column(name = "attempt_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer attemptCount = 0;

    @Column(name = "correct_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer correctCount = 0;

    @Column(name = "wrong_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer wrongCount = 0;

    @Column(nullable = false, precision = 5, scale = 2,
            columnDefinition = "DECIMAL(5,2) NOT NULL DEFAULT 0")
    private BigDecimal score = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, precision = 5, scale = 4,
            columnDefinition = "DECIMAL(5,4) NOT NULL DEFAULT 0")
    private BigDecimal confidence = BigDecimal.ZERO.setScale(4);

    @Column(nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'new'")
    private String status = "new";

    @Column(name = "next_review_at")
    private LocalDateTime nextReviewAt;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    protected void validateAndDefault() {
        if (attemptCount == null) attemptCount = 0;
        if (correctCount == null) correctCount = 0;
        if (wrongCount == null) wrongCount = 0;
        if (appliedAttemptIdsJson == null) appliedAttemptIdsJson = "[]";
        if (score == null) score = BigDecimal.ZERO.setScale(2);
        if (confidence == null) confidence = BigDecimal.ZERO.setScale(4);
        if (status == null) status = "new";
        if (!LearningStatuses.MASTERY.contains(status)) {
            throw new IllegalStateException("Unsupported mastery status: " + status);
        }
    }
}
