package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "favorite_destination", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "marker_id"})
})
@Schema(description = "收藏目的地实体")
public class FavoriteDestination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "收藏ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Column(name = "marker_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '标记ID'")
    @Schema(description = "标记ID", example = "1")
    private Long markerId;

    @Column(name = "marker_name", length = 100, columnDefinition = "VARCHAR(100) COMMENT '标记名称（快照）'")
    @Schema(description = "标记名称（快照）", example = "第一学生餐厅")
    private String markerName;

    @Column(precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '经度（快照）'")
    @Schema(description = "经度（快照）", example = "116.397428")
    private BigDecimal longitude;

    @Column(precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '纬度（快照）'")
    @Schema(description = "纬度（快照）", example = "39.90923")
    private BigDecimal latitude;

    @Column(name = "facility_type", columnDefinition = "INT COMMENT '设施类型（快照）'")
    @Schema(description = "设施类型（快照）", example = "1")
    private Integer facilityType;

    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '用户备注'")
    @Schema(description = "用户备注", example = "食堂")
    private String remark;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marker_id", insertable = false, updatable = false)
    private MapMarker marker;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
