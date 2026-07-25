package com.example.appbackend.entity;

import com.example.appbackend.domain.LearningStatuses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_path", uniqueConstraints = {
        @UniqueConstraint(name = "uk_learning_path_user_course_version",
                columnNames = {"user_id", "course_key", "version_no"})
}, indexes = {
        @Index(name = "idx_learning_path_user_course_status",
                columnList = "user_id,course_key,status")
})
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_key", nullable = false, length = 40)
    private String courseKey;

    @Column(nullable = false, length = 500)
    private String goal;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'active'")
    private String status = "active";

    @Column(name = "profile_digest", nullable = false, length = 128)
    private String profileDigest;

    @Column(name = "mastery_digest", nullable = false, length = 128)
    private String masteryDigest;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "next_replan_at")
    private LocalDateTime nextReplanAt;

    @PrePersist
    @PreUpdate
    protected void validateAndDefault() {
        if (status == null) status = "active";
        if (generatedAt == null) generatedAt = LocalDateTime.now();
        if (!LearningStatuses.PATH.contains(status)) {
            throw new IllegalStateException("Unsupported path status: " + status);
        }
    }
}
