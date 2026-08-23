package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 校园课程类型字典：typeCode 对应 campus_course.course_type / custom_course_types 的存储值。
 * category 区分两类：BUILTIN-必选类型（内置），CUSTOM-自定义类型（管理员创建）。
 */
@Data
@Entity
@Table(name = "campus_course_type", indexes = {
        @Index(name = "idx_campus_course_type_sort", columnList = "sort_order,id"),
        @Index(name = "uk_campus_course_type_code", columnList = "type_code", unique = true)
})
public class CampusCourseType {
    public static final String CATEGORY_BUILTIN = "BUILTIN";
    public static final String CATEGORY_CUSTOM = "CUSTOM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 20)
    private String typeName;

    @Column(name = "category", nullable = false, length = 20)
    private String category = CATEGORY_CUSTOM;

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
