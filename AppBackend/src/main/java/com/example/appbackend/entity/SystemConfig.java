package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", unique = true, nullable = false, length = 100,
            columnDefinition = "VARCHAR(100) NOT NULL COMMENT '配置键'")
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '配置值'")
    private String configValue;

    @Column(name = "config_group", length = 50, columnDefinition = "VARCHAR(50) COMMENT '配置分组'")
    private String configGroup;

    @Column(length = 255, columnDefinition = "VARCHAR(255) COMMENT '配置说明'")
    private String description;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用'")
    private Integer status = 1;

    @Column(name = "is_default", columnDefinition = "TINYINT DEFAULT 0 COMMENT '是否为默认配置: 0-否, 1-是'")
    private Integer isDefault = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
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
