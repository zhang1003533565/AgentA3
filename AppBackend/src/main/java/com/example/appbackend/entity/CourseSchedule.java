package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "course_schedule")
public class CourseSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户 ID（学号关联）'")
    private Long userId;

    @Column(name = "student_id", length = 50, columnDefinition = "VARCHAR(50) COMMENT '学号'")
    private String studentId;

    @Column(name = "course_name", nullable = false, length = 200, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '课程名称'")
    private String courseName;

    @Column(name = "week_range", length = 200, columnDefinition = "VARCHAR(200) COMMENT '周数范围，如：1-2 周，3-5 周'")
    private String weekRange;

    @Column(name = "class_sessions", length = 50, columnDefinition = "VARCHAR(50) COMMENT '节次，如：1-2 节，3-4 节'")
    private String classSessions;

    @Column(name = "weekday", columnDefinition = "INT COMMENT '星期几：1-星期一，2-星期二，3-星期三，4-星期四，5-星期五，6-星期六，7-星期日'")
    private Integer weekday;

    @Column(name = "location", length = 100, columnDefinition = "VARCHAR(100) COMMENT '上课地点'")
    private String location;

    @Column(name = "campus", length = 50, columnDefinition = "VARCHAR(50) COMMENT '校区'")
    private String campus;

    @Column(name = "teacher_name", length = 50, columnDefinition = "VARCHAR(50) COMMENT '教师姓名'")
    private String teacherName;

    @Column(name = "class_code", length = 100, columnDefinition = "VARCHAR(100) COMMENT '教学班号'")
    private String classCode;

    @Column(name = "class_composition", length = 255, columnDefinition = "VARCHAR(255) COMMENT '教学班组成'")
    private String classComposition;

    @Column(name = "assessment_type", length = 20, columnDefinition = "VARCHAR(20) COMMENT '考核方式：考试/考查'")
    private String assessmentType;

    @Column(name = "theory_hours", columnDefinition = "INT DEFAULT 0 COMMENT '理论学时'")
    private Integer theoryHours = 0;

    @Column(name = "lab_hours", columnDefinition = "INT DEFAULT 0 COMMENT '实验/上机学时'")
    private Integer labHours = 0;

    @Column(name = "weekly_hours", columnDefinition = "INT DEFAULT 0 COMMENT '周学时'")
    private Integer weeklyHours = 0;

    @Column(name = "total_hours", columnDefinition = "INT DEFAULT 0 COMMENT '总学时'")
    private Integer totalHours = 0;

    @Column(name = "credit", columnDefinition = "DECIMAL(3,1) DEFAULT 0 COMMENT '学分'")
    private java.math.BigDecimal credit = java.math.BigDecimal.ZERO;

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