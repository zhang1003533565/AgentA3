package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "map_marker")
@Schema(description = "地图标记实体")
public class MapMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "标记ID", example = "1")
    private Long id;

    @Column(name = "facility_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '关联设施ID'")
    @Schema(description = "关联设施ID", example = "1")
    private Long facilityId;

    @Column(name = "icon_url", length = 255, columnDefinition = "VARCHAR(255) COMMENT '自定义图标URL'")
    @Schema(description = "自定义图标URL", example = "https://xxx.com/icons/restaurant.png")
    private String iconUrl;

    @Column(columnDefinition = "INT DEFAULT 0 COMMENT '排序'")
    @Schema(description = "排序", example = "1")
    private Integer sort = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

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
