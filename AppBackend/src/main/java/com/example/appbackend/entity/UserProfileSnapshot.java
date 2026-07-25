package com.example.appbackend.entity;

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

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_profile_snapshot", indexes = {
        @Index(name = "idx_user_profile_snapshot_user", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profile_snapshot_user", columnNames = "user_id")
})
public class UserProfileSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false,
            columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Lob
    @Column(name = "snapshot_json", nullable = false,
            columnDefinition = "LONGTEXT NOT NULL COMMENT '完整画像快照JSON'")
    private String snapshotJson;

    @Column(name = "snapshot_version", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) NOT NULL COMMENT '快照结构版本'")
    private String snapshotVersion;

    @Column(name = "generated_at", nullable = false,
            columnDefinition = "DATETIME NOT NULL COMMENT '快照生成时间'")
    private LocalDateTime generatedAt;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (generatedAt == null) {
            generatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
