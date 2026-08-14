package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程资料池表：一门课程下可上传的资料文件（视频/文档/图片等）。
 * 与旧的 campus_course_chapter.resource_url 相互独立，章节通过 material_ids 引用本表。
 */
@Data
@Entity
@Table(name = "campus_course_material", indexes = {
        @Index(name = "idx_campus_material_course", columnList = "course_id"),
        @Index(name = "idx_campus_material_batch", columnList = "upload_batch_id")
})
public class CampusCourseMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 视频/音频总时长（秒），文档可存总页数；默认 0 表示不做自动完成判定。 */
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 0;

    /** 软删除标记：false-正常 true-已下架（保留文件与 URL）。 */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    /** 批次 ID，用于追踪同一文件夹（可跨请求）上传的文件并累计校验总大小。 */
    @Column(name = "upload_batch_id", length = 64)
    private String uploadBatchId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (deleted == null) deleted = false;
        if (durationSeconds == null) durationSeconds = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
