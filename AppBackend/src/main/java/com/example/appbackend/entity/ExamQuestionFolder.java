package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_question_folder", indexes = {
        @Index(name = "idx_eqf_visibility_owner", columnList = "visibility,owner_user_id"),
        @Index(name = "idx_eqf_owner", columnList = "owner_user_id"),
        @Index(name = "idx_eqf_status", columnList = "status")
})
public class ExamQuestionFolder {

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '收藏夹名称'")
    private String name;

    @Column(nullable = false, length = 16,
            columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '可见范围：PUBLIC/PRIVATE'")
    private String visibility = VISIBILITY_PRIVATE;

    @Column(name = "owner_user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '创建者用户ID'")
    private Long ownerUserId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-删除'")
    private Integer status = 1;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
        if (visibility == null || visibility.isBlank()) {
            visibility = VISIBILITY_PRIVATE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
