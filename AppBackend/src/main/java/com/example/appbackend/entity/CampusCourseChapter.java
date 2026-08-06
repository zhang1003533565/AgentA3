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

    /**
     * 引用的资料池 ID 数组，JSON 格式如 [1,2,3]。
     * 仅允许视频类型资料，且最多一个。由 CourseMaterialService 维护。
     */
    @Column(name = "material_ids", columnDefinition = "TEXT")
    private String materialIds;

    /**
     * 附加下载资料 ID 数组，JSON 格式如 [1,2,3]。
     * 允许非视频类型资料（文本/PDF/文档等），可多个。
     */
    @Column(name = "additional_material_ids", columnDefinition = "TEXT")
    private String additionalMaterialIds;

    /**
     * Word 文本资料 ID 数组，JSON 格式如 [1,2,3]。
     * 仅允许 Word 类型（doc/docx），可多个。
     */
    @Column(name = "word_material_ids", columnDefinition = "TEXT")
    private String wordMaterialIds;

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
