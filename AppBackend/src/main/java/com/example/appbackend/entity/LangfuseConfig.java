package com.example.appbackend.entity;

import com.example.appbackend.persistence.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "langfuse_config")
public class LangfuseConfig {

    /** A singleton configuration row managed by the administration console. */
    @Id
    private Long id = 1L;

    @Column(nullable = false, columnDefinition = "TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 Langfuse 观测'")
    private Boolean enabled = false;

    @Column(name = "base_url", length = 500, columnDefinition = "VARCHAR(500) COMMENT 'Langfuse 服务地址'")
    private String baseUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "public_key", columnDefinition = "TEXT COMMENT '加密保存的 Langfuse Public Key'")
    private String publicKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_key", columnDefinition = "TEXT COMMENT '加密保存的 Langfuse Secret Key'")
    private String secretKey;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = createTime;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
