package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "forum_report_audit_log")
@Schema(description = "论坛举报审计日志")
public class ForumReportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "target_type")
    private Integer targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
