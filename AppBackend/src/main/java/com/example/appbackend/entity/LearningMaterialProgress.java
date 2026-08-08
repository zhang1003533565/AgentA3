package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 精细化学习进度表：记录用户对单个资料（material）的观看进度。
 * 与旧表 campus_course_progress（章节级布尔打卡）相互独立，互不冲突。
 */
@Data
@Entity
@Table(name = "learning_material_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_material", columnNames = {"user_id", "material_id"})
}, indexes = {
        @Index(name = "idx_material_progress_user", columnList = "user_id")
})
public class LearningMaterialProgress {

    public static final int STATUS_NOT_STARTED = 0;
    public static final int STATUS_LEARNING = 1;
    public static final int STATUS_COMPLETED = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    /** 已观看秒数/已读页数。 */
    @Column(name = "watch_seconds", nullable = false)
    private Integer watchSeconds = 0;

    /** 0-未开始 1-学习中 2-已完成。 */
    @Column(name = "status", nullable = false)
    private Integer status = STATUS_NOT_STARTED;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    @PreUpdate
    void onWrite() {
        updateTime = LocalDateTime.now();
        if (watchSeconds == null) watchSeconds = 0;
        if (status == null) status = STATUS_NOT_STARTED;
    }
}
