package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "maxkb_account")
public class MaxKbAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_name", nullable = false, length = 100,
            columnDefinition = "VARCHAR(100) NOT NULL COMMENT 'MaxKB账号名称'")
    private String accountName;

    @Column(name = "base_url", nullable = false, length = 500,
            columnDefinition = "VARCHAR(500) NOT NULL COMMENT 'MaxKB服务地址'")
    private String baseUrl;

    @Column(nullable = false, length = 32,
            columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'local' COMMENT '环境：local/test/prod/custom'")
    private String environment = "local";

    @Column(name = "api_key", nullable = false, columnDefinition = "TEXT NOT NULL COMMENT 'MaxKB Knowledge OpenAPI Key'")
    private String apiKey;

    @Column(name = "workspace_id", nullable = false, length = 128,
            columnDefinition = "VARCHAR(128) NOT NULL COMMENT 'MaxKB工作空间ID'")
    private String workspaceId;

    @Column(length = 255, columnDefinition = "VARCHAR(255) COMMENT '备注'")
    private String remark;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用'")
    private Integer status = 1;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
