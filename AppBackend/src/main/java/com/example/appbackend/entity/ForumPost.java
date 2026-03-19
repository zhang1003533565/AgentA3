package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_post")
@Schema(description = "论坛帖子实体")
public class ForumPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "帖子ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '发布者ID'")
    @Schema(description = "发布者ID", example = "1")
    private Long userId;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '帖子标题'")
    @Schema(description = "帖子标题", example = "关于考研经验分享")
    private String title;

    @Column(columnDefinition = "TEXT COMMENT '帖子内容'")
    @Schema(description = "帖子内容", example = "分享一下我的考研经验...")
    private String content;

    @Column(length = 2000, columnDefinition = "VARCHAR(2000) COMMENT '图片URL列表(JSON格式)'")
    @Schema(description = "图片URL列表(JSON格式)", example = "[\"https://example.com/img1.jpg\"]")
    private String images;

    @Column(name = "topic_id", columnDefinition = "BIGINT COMMENT '话题ID'")
    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Column(name = "view_count", columnDefinition = "INT DEFAULT 0 COMMENT '浏览量'")
    @Schema(description = "浏览量", example = "100")
    private Integer viewCount = 0;

    @Column(name = "like_count", columnDefinition = "INT DEFAULT 0 COMMENT '点赞数'")
    @Schema(description = "点赞数", example = "50")
    private Integer likeCount = 0;

    @Column(name = "comment_count", columnDefinition = "INT DEFAULT 0 COMMENT '评论数'")
    @Schema(description = "评论数", example = "20")
    private Integer commentCount = 0;

    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '帖子状态: DRAFT-草稿, PUBLISHED-已发布, HIDDEN-已隐藏, DELETED-已删除'")
    @Schema(description = "帖子状态: DRAFT-草稿, PUBLISHED-已发布, HIDDEN-已隐藏, DELETED-已删除", example = "PUBLISHED")
    private String status = "PUBLISHED";

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", insertable = false, updatable = false)
    private ForumTopic topic;

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
