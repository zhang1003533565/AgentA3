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
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_path_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_learning_path_item_key",
                columnNames = {"path_id", "item_key"}),
        @UniqueConstraint(name = "uk_learning_path_item_sequence",
                columnNames = {"path_id", "sequence_no"})
}, indexes = {
        @Index(name = "idx_learning_path_item_path_status", columnList = "path_id,status")
})
public class LearningPathItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "path_id", nullable = false)
    private Long pathId;

    @Column(name = "item_key", nullable = false, length = 120)
    private String itemKey;

    @Column(name = "knowledge_point", nullable = false, length = 160)
    private String knowledgePoint;

    @Column(nullable = false, length = 500)
    private String objective;

    @Column(name = "target_mastery", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetMastery;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Lob
    @Column(name = "resource_kinds_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL")
    private String resourceKindsJson;

    @Lob
    @Column(name = "resource_ids_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL")
    private String resourceIdsJson;

    @Column(nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'locked'")
    private String status = "locked";

    @Column(name = "delivery_status", nullable = false, length = 30,
            columnDefinition = "VARCHAR(30) NOT NULL DEFAULT 'pending'")
    private String deliveryStatus = "pending";

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @PrePersist
    @PreUpdate
    protected void validateAndDefault() {
        if (status == null) status = "locked";
        if (deliveryStatus == null) deliveryStatus = "pending";
        if (!LearningStatuses.ITEM.contains(status)) {
            throw new IllegalStateException("Unsupported path item status: " + status);
        }
    }
}
