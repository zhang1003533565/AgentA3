package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_topic")
@Schema(description = "论坛话题/标签实体")
public class ForumTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "话题ID", example = "1")
    private Long id;

    @Column(name = "topic_name", nullable = false, unique = true, length = 50, columnDefinition = "VARCHAR(50) NOT NULL COMMENT '话题名称'")
    @Schema(description = "话题名称", example = "考研经验")
    private String topicName;

    @Column(name = "topic_icon", length = 255, columnDefinition = "VARCHAR(255) COMMENT '话题图标URL'")
    @Schema(description = "话题图标URL", example = "https://example.com/icon.png")
    private String topicIcon;

    @Column(length = 200, columnDefinition = "VARCHAR(200) COMMENT '话题描述'")
    @Schema(description = "话题描述", example = "分享考研经验和学习方法")
    private String description;

    @Column(name = "post_count", columnDefinition = "INT DEFAULT 0 COMMENT '帖子数量'")
    @Schema(description = "帖子数量", example = "100")
    private Integer postCount = 0;

    @Column(name = "is_hot", columnDefinition = "INT DEFAULT 0 COMMENT '是否热门: 0-否, 1-是'")
    @Schema(description = "是否热门: 0-否, 1-是", example = "1")
    private Integer isHot = 0;

    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '话题状态: ACTIVE-启用, INACTIVE-禁用'")
    @Schema(description = "话题状态: ACTIVE-启用, INACTIVE-禁用", example = "ACTIVE")
    private String status = "ACTIVE";

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
