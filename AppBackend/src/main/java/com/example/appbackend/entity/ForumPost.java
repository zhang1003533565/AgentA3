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
    private Long userId;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '帖子标题'")
    private String title;

    @Column(columnDefinition = "TEXT COMMENT '帖子内容'")
    private String content;

    @Column(length = 2000, columnDefinition = "VARCHAR(2000) COMMENT '图片URL列表(JSON格式)'")
    private String images;

    @Column(name = "topic_id", columnDefinition = "BIGINT COMMENT '话题ID'")
    private Long topicId;

    @Column(name = "view_count", columnDefinition = "INT DEFAULT 0 COMMENT '浏览量'")
    private Integer viewCount = 0;

    @Column(name = "like_count", columnDefinition = "INT DEFAULT 0 COMMENT '点赞数'")
    private Integer likeCount = 0;

    @Column(name = "comment_count", columnDefinition = "INT DEFAULT 0 COMMENT '评论数'")
    private Integer commentCount = 0;

    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED-已发布，HIDDEN-已隐藏，DELETED-已删除'")
    private String status = "PUBLISHED";

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
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
        if (viewCount == null) {
            viewCount = 0;
        }
        if (likeCount == null) {
            likeCount = 0;
        }
        if (commentCount == null) {
            commentCount = 0;
        }
        if (status == null) {
            status = "PUBLISHED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
