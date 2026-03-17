package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sign_in", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"activity_id", "user_id"})
})
public class SignIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '活动ID'")
    private Long activityId;

    @Column(name = "registration_id", columnDefinition = "BIGINT COMMENT '报名ID'")
    private Long registrationId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户ID'")
    private Long userId;

    @Column(name = "sign_in_time", columnDefinition = "DATETIME COMMENT '签到时间'")
    private LocalDateTime signInTime;

    @Column(name = "sign_in_status", columnDefinition = "INT DEFAULT 0 COMMENT '签到状态: 0-未签到, 1-已签到'")
    private Integer signInStatus = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", insertable = false, updatable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
