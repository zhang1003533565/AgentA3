package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campus_course_chapter", indexes = {
        @Index(name = "idx_course_chapter_sort", columnList = "course_id,sort_order")
})
public class CampusCourseChapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "resource_type", length = 30)
    private String resourceType;

    @Column(name = "resource_url", length = 500)
    private String resourceUrl;

    /**
     * 引用的资料池 ID 数组，JSON 格式如 [1,2,3]。
     * 由新模块（资料/进度）维护，旧的 CampusCourseService 不读写此字段。
     */
    @Column(name = "material_ids", columnDefinition = "TEXT")
    private String materialIds;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "required_chapter", nullable = false)
    private Boolean required = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = createTime;
    }

    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
