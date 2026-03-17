package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "registration", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"activity_id", "user_id"})
})
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '活动ID'")
    private Long activityId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING' COMMENT '报名状态: PENDING-待审核, APPROVED-已通过, REJECTED-已拒绝'")
    private String status = "PENDING";

    @Column(name = "signup_time", columnDefinition = "DATETIME COMMENT '报名时间'")
    private LocalDateTime signupTime;

    @Column(name = "audit_time", columnDefinition = "DATETIME COMMENT '审核时间'")
    private LocalDateTime auditTime;

    @Column(name = "audit_by", columnDefinition = "BIGINT COMMENT '审核人ID'")
    private Long auditBy;

    @Column(length = 500, columnDefinition = "VARCHAR(500) COMMENT '备注'")
    private String remark;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        signupTime = LocalDateTime.now();
        createTime = LocalDateTime.now();
    }
}
