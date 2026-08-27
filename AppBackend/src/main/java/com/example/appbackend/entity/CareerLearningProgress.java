package com.example.appbackend.entity;

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
@Table(
        name = "career_learning_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_career_progress_user_item", columnNames = {"user_id", "item_id"}),
        indexes = @Index(name = "idx_career_progress_user", columnList = "user_id")
)
public class CareerLearningProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "career_id", nullable = false, length = 100)
    private String careerId;

    @Column(name = "skill_id", nullable = false, length = 100)
    private String skillId;

    @Column(name = "item_id", nullable = false, length = 100)
    private String itemId;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
