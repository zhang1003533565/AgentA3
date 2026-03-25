package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "navigation_log")
@Schema(description = "导航记录实体")
public class NavigationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "导航记录ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Column(name = "from_longitude", precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '起点经度'")
    @Schema(description = "起点经度", example = "116.397000")
    private BigDecimal fromLongitude;

    @Column(name = "from_latitude", precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '起点纬度'")
    @Schema(description = "起点纬度", example = "39.908000")
    private BigDecimal fromLatitude;

    @Column(name = "to_marker_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '目标标记ID'")
    @Schema(description = "目标标记ID", example = "1")
    private Long toMarkerId;

    @Column(precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) COMMENT '导航距离（米）'")
    @Schema(description = "导航距离（米）", example = "150.5")
    private BigDecimal distance;

    @Column(columnDefinition = "INT COMMENT '预计时长（秒）'")
    @Schema(description = "预计时长（秒）", example = "120")
    private Integer duration;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态: 1-进行中 2-已完成 3-已取消'")
    @Schema(description = "状态: 1-进行中 2-已完成 3-已取消", example = "1")
    private Integer status = 1;

    @Column(name = "arrive_time", columnDefinition = "DATETIME COMMENT '实际到达时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际到达时间", example = "2026-03-24 10:05:00")
    private LocalDateTime arriveTime;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_marker_id", insertable = false, updatable = false)
    private MapMarker marker;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

    public enum Status {
        IN_PROGRESS(1, "进行中"),
        COMPLETED(2, "已完成"),
        CANCELLED(3, "已取消");

        private final Integer value;
        private final String description;

        Status(Integer value, String description) {
            this.value = value;
            this.description = description;
        }

        public Integer getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }
}
