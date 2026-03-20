package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_like", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "target_id"})
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

    @Column(name = "target_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '帖子ID'")
    @Schema(description = "帖子ID", example = "1")
    private Long targetId;

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
