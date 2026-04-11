package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "announcement")
@Schema(description = "公告实体")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "公告ID", example = "1")
    private Long id;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '公告标题'")
    @Schema(description = "公告标题", example = "关于2026年春季学期开学通知", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '公告内容'")
    @Schema(description = "公告内容", example = "各位师生员工，新学期将于3月1日正式开始...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0 COMMENT '排序，值越小越靠前'")
    @Schema(description = "排序，值越小越靠前", example = "0")
    private Integer sortOrder = 0;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用'")
    @Schema(description = "是否启用: true-启用, false-禁用", example = "true")
    private Boolean enabled = true;

    @Column(name = "is_top", nullable = false, columnDefinition = "TINYINT DEFAULT 0 COMMENT '是否置顶: 0-否, 1-是'")
    @Schema(description = "是否置顶: true-置顶, false-不置顶", example = "false")
    private Boolean isTop = false;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
