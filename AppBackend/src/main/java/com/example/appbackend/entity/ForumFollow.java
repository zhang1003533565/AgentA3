package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_follow", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "follow_id"})
})
@Schema(description = "论坛关注实体")
public class ForumFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "关注ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID(关注者)'")
    @Schema(description = "用户ID(关注者)", example = "1")
    private Long userId;

    @Column(name = "follow_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '被关注用户ID'")
    @Schema(description = "被关注用户ID", example = "2")
    private Long followId;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '关注时间'")
    @Schema(description = "关注时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_id", insertable = false, updatable = false)
    private User followUser;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
