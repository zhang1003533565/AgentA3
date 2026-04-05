package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50, columnDefinition = "VARCHAR(50) NOT NULL COMMENT '用户名'")
    private String username;

    @Column(nullable = false, columnDefinition = "VARCHAR(255) NOT NULL COMMENT '密码'")
    private String password;

    @Column(name = "real_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '真实姓名'")
    private String realName;

    @Column(length = 20, columnDefinition = "VARCHAR(20) COMMENT '电话'")
    private String phone;

    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '邮箱'")
    private String email;

    @Column(name = "personal_number", length = 50, columnDefinition = "VARCHAR(50) COMMENT '个人编号'")
    private String personalNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", columnDefinition = "BIGINT COMMENT '角色 ID'")
    private Role role;

    @Column(length = 255, columnDefinition = "VARCHAR(255) COMMENT '头像'")
    private String avatar;

    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '学院'")
    private String college;

    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '专业'")
    private String major;

    @Column(name = "class_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '班级'")
    private String className;

    @Column(name = "jwx_student_id", length = 50, columnDefinition = "VARCHAR(50) COMMENT '教务系统学号'")
    private String jwxStudentId;

    @Column(name = "share_code", length = 32, unique = true, columnDefinition = "VARCHAR(32) UNIQUE COMMENT '课表分享码'")
    private String shareCode;

    @Column(name = "jwx_password", length = 100, columnDefinition = "VARCHAR(100) COMMENT '教务系统密码'")
    private String jwxPassword;

    @Column(name = "semester_start", columnDefinition = "DATE COMMENT '学期开始日期'")
    private java.time.LocalDate semesterStart;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-禁用'")
    private Integer status = 1;

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