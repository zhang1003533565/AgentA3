package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "map_config")
@Schema(description = "地图配置实体")
public class MapConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "配置ID", example = "1")
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true, length = 100, columnDefinition = "VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键'")
    @Schema(description = "配置键", example = "map_center_longitude", requiredMode = Schema.RequiredMode.REQUIRED)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT COMMENT '配置值'")
    @Schema(description = "配置值", example = "116.397428")
    private String configValue;

    @Column(length = 255, columnDefinition = "VARCHAR(255) COMMENT '配置说明'")
    @Schema(description = "配置说明", example = "地图中心经度")
    private String description;

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

    public static class ConfigKeys {
        public static final String MAP_CENTER_LONGITUDE = "map_center_longitude";
        public static final String MAP_CENTER_LATITUDE = "map_center_latitude";
        public static final String MAP_ZOOM_LEVEL = "map_zoom_level";
        public static final String MAP_BOUNDARY = "map_boundary";
    }
}
