package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 架构图生成记录。
 * 由 Hibernate ddl-auto=update 自动建表，无需手写 SQL。
 */
@Data
@Entity
@Table(name = "architecture_record")
@Schema(description = "AI 架构图生成记录")
public class ArchitectureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "记录ID")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Column(nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '架构标题'")
    private String title;

    @Column(columnDefinition = "VARCHAR(1000) COMMENT '需求描述'")
    private String description;

    @Column(name = "system_type", length = 40, columnDefinition = "VARCHAR(40) COMMENT '系统类型 WEB/APP/MINI_PROGRAM/ADMIN/MICROSERVICE/IOT/AI'")
    private String systemType;

    @Column(name = "architecture_style", length = 40, columnDefinition = "VARCHAR(40) COMMENT '架构模式 AUTO/MONOLITH/FRONT_BACKEND_SEPARATION/MICROSERVICE/CLOUD_NATIVE'")
    private String architectureStyle;

    @Column(name = "config_json", columnDefinition = "LONGTEXT COMMENT '前端配置项JSON（layers/displayContent/relationType）'")
    private String configJson;

    @Column(name = "architecture_json", columnDefinition = "LONGTEXT COMMENT 'AI 生成的架构JSON（title/nodes/edges）'")
    private String architectureJson;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
