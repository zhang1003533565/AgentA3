package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "facility_review")
@Schema(description = "设施评价实体")
public class FacilityReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "评价ID", example = "1")
    private Long id;

    @Column(name = "facility_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '设施ID'")
    @Schema(description = "设施ID", example = "1")
    private Long facilityId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Column(nullable = false, columnDefinition = "INT NOT NULL COMMENT '评分: 1-5'")
    @Schema(description = "评分: 1-5", example = "5")
    private Integer score;

    @Column(columnDefinition = "TEXT COMMENT '评价内容'")
    @Schema(description = "评价内容", example = "味道很棒！")
    private String content;

    @Column(columnDefinition = "TEXT COMMENT '图片列表(JSON数组)'")
    @Schema(description = "图片列表(JSON数组)", example = "[]")
    private String images;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", insertable = false, updatable = false)
    private CampusFacility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
