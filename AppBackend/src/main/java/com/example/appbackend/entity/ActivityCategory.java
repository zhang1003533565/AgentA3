package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "activity_category")
public class ActivityCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '分类名称'")
    private String categoryName;

    @Column(columnDefinition = "INT DEFAULT 1 COMMENT '排序'")
    private Integer sort = 1;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用'")
    private Integer status = 1;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
