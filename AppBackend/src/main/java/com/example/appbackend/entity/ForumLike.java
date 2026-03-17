package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_like", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "target_id", "target_type"})
})
@Schema(description = "论坛点赞实体")
public class ForumLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "点赞ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Column(name = "target_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '目标ID(帖子ID或评论ID)'")
    @Schema(description = "目标ID(帖子ID或评论ID)", example = "1")
    private Long targetId;

    @Column(name = "target_type", nullable = false, columnDefinition = "INT NOT NULL COMMENT '目标类型: 1-帖子, 2-评论'")
    @Schema(description = "目标类型: 1-帖子, 2-评论", example = "1")
    private Integer targetType;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '点赞时间'")
    @Schema(description = "点赞时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
