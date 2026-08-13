package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "secondhand_browse_history", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "item_id"})
})
@Schema(description = "浏览历史实体")
public class SecondhandBrowseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "记录ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "3")
    private Long userId;

    @Column(name = "item_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '物品ID'")
    @Schema(description = "物品ID", example = "10")
    private Long itemId;

    @Column(name = "browse_time", columnDefinition = "DATETIME COMMENT '浏览时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "浏览时间")
    private LocalDateTime browseTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    private SecondhandItem item;

    @PrePersist
    protected void onCreate() {
        browseTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        browseTime = LocalDateTime.now();
    }
}
