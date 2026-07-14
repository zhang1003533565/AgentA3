package com.example.appbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "ai_leader_generated_export",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_leader_export_storage", columnNames = "storage_key"),
                @UniqueConstraint(name = "uk_ai_leader_export_message_resource", columnNames = {"message_id", "resource_id"})
        },
        indexes = @Index(
                name = "idx_ai_leader_export_owner",
                columnList = "user_id,leader_session_id,message_id,storage_key,status"
        )
)
public class AiLeaderGeneratedExport {

    public static final String STATUS_ACTIVE = "active";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leader_session_id", nullable = false)
    private Long leaderSessionId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "resource_id", nullable = false, length = 80)
    private String resourceId;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "file_name", nullable = false, length = 240)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 160)
    private String mimeType;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "python_capability", nullable = false, length = 200)
    @ToString.Exclude
    private String pythonCapability;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;
}
