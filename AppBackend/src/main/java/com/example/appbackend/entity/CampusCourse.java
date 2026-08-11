package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campus_course", indexes = {
        @Index(name = "idx_campus_course_status_sort", columnList = "publish_status,sort_order"),
        @Index(name = "idx_campus_course_owner", columnList = "owner_id")
})
public class CampusCourse {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String AUDIENCE_ALL = "ALL";
    public static final String AUDIENCE_CLASS = "CLASS";
    public static final String AUDIENCE_STUDENT = "STUDENT";
    public static final String COURSE_TYPE_REQUIRED = "REQUIRED";
    public static final String COURSE_TYPE_ELECTIVE = "ELECTIVE";
    public static final String COURSE_TYPE_PUBLIC = "PUBLIC";
    public static final String COURSE_TYPE_LAB = "LAB";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "book_title", nullable = false, length = 160)
    private String bookTitle;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "display_image_url", length = 500)
    private String displayImageUrl;

    @Column(length = 2000)
    private String description;

    @Column(length = 40)
    private String semester;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType = "ADMIN";

    /** 课程类型：REQUIRED-必修, ELECTIVE-选修, PUBLIC-公共课, LAB-实验课 */
    @Column(name = "course_type", length = 10)
    private String courseType;

    /** 自定义课程类型：逗号分隔的类型代码列表，对应 campus_course_type 中 category=CUSTOM 的项 */
    @Column(name = "custom_course_types", columnDefinition = "TEXT")
    private String customCourseTypes;

    @Column(name = "audience_type", nullable = false, length = 20)
    private String audienceType = AUDIENCE_ALL;

    @Column(name = "audience_values", columnDefinition = "TEXT")
    private String audienceValues;

    @Column(name = "publish_status", nullable = false, length = 20)
    private String publishStatus = STATUS_DRAFT;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

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
