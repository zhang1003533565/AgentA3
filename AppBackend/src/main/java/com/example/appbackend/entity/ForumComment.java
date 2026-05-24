package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_comment")
@Schema(description = "论坛评论实体")
public class ForumComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "评论ID", example = "1")
    private Long id;

    @Column(name = "post_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '帖子ID'")
    private Long postId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '评论者ID'")
    private Long userId;

    @Column(name = "parent_id", columnDefinition = "BIGINT COMMENT '父评论ID'")
    private Long parentId;

    @Column(name = "reply_to_id", columnDefinition = "BIGINT COMMENT '回复用户ID'")
    private Long replyToId;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '评论内容'")
    private String content;

    @Column(name = "like_count", columnDefinition = "INT DEFAULT 0 COMMENT '点赞数'")
    private Integer likeCount = 0;

    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'NORMAL' COMMENT '状态：NORMAL-正常，HIDDEN-已隐藏，DELETED-已删除'")
    private String status = "NORMAL";

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    private ForumPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private ForumComment parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id", insertable = false, updatable = false)
    private User replyToUser;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = "NORMAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
