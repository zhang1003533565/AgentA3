package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_config_test_log")
public class SystemConfigTestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id")
    private Long configId;

    @Column(name = "config_key", nullable = false, length = 160)
    private String configKey;

    @Column(name = "success", nullable = false)
    private Boolean success = false;

    @Column(name = "target", length = 500)
    private String target;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "provider", length = 80)
    private String provider;

    @Column(name = "model", length = 160)
    private String model;

    @Column(name = "modality", length = 40)
    private String modality;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
