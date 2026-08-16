package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "schedule_semester_setting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "academic_year", "semester_term"})
)
public class ScheduleSemesterSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户 ID'")
    private Long userId;

    @Column(name = "academic_year", nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL COMMENT '学年，如 2025-2026'")
    private String academicYear;

    @Column(name = "semester_term", nullable = false, columnDefinition = "INT NOT NULL COMMENT '学期：1-第一学期，2-第二学期'")
    private Integer semesterTerm;

    @Column(name = "semester_code", nullable = false, length = 10, columnDefinition = "VARCHAR(10) NOT NULL COMMENT '教务系统学期代码，如 3/12'")
    private String semesterCode;

    @Column(name = "semester_start", columnDefinition = "DATE COMMENT '本学期开学日期'")
    private LocalDate semesterStart;

    @Column(name = "selected_flag", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前选中学期'")
    private Boolean selectedFlag = false;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (selectedFlag == null) {
            selectedFlag = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (selectedFlag == null) {
            selectedFlag = false;
        }
    }
}
