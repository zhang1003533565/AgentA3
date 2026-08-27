package com.example.appbackend.entity;

import com.example.appbackend.persistence.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Model Config Entity
 * 对应数据库表：ai_model_config
 */
@Data
@Entity
@Table(name = "ai_model_config")
public class AiModelConfig {

    @Id
    private Long id = 1L;

    @Column(name = "provider", length = 100, nullable = false)
    private String provider;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
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
