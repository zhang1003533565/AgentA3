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

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 20)
    private String status = "PENDING";

    @Column(name = "signup_time")
    private LocalDateTime signupTime;

    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    @Column(name = "audit_by")
    private Long auditBy;

    @Column(length = 500)
    private String remark;

    @Column(name = "create_time")
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
