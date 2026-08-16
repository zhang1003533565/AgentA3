package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(
        name = "schedule_period_setting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period_index"})
)
public class SchedulePeriodSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '用户 ID'")
    private Long userId;

    @Column(name = "period_index", nullable = false, columnDefinition = "INT NOT NULL COMMENT '第几节课'")
    private Integer periodIndex;

    @Column(name = "start_time", nullable = false, columnDefinition = "TIME NOT NULL COMMENT '开始时间'")
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, columnDefinition = "TIME NOT NULL COMMENT '结束时间'")
    private LocalTime endTime;

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
