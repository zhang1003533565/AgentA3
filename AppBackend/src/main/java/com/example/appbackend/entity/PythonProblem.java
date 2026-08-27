package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Python 在线编程题库题目。
 * tags / examples / testcases / similarIds 以 JSON 字符串存储，
 * 由服务层负责与结构化对象互转（保持判题接口 /api/code/execute 的入参格式不变）。
 */
@Data
@Entity
@Table(name = "python_problem")
@Schema(description = "Python 在线编程题目")
public class PythonProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "题目ID", example = "1")
    private Long id;

    @Column(nullable = false, columnDefinition = "INT NOT NULL COMMENT '题号（展示用，唯一）'")
    @Schema(description = "题号", example = "1")
    private Integer number;

    @Column(nullable = false, length = 128, columnDefinition = "VARCHAR(128) NOT NULL COMMENT '标题'")
    @Schema(description = "标题", example = "两数之和")
    private String title;

    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16) NOT NULL COMMENT '难度: easy/medium/hard'")
    @Schema(description = "难度: easy/medium/hard", example = "easy")
    private String difficulty;

    @Column(name = "pass_rate", columnDefinition = "DOUBLE COMMENT '通过率（展示用）'")
    @Schema(description = "通过率", example = "45.2")
    private Double passRate;

    @Column(length = 32, columnDefinition = "VARCHAR(32) COMMENT '提交数（展示用）'")
    @Schema(description = "提交数", example = "12.1M")
    private String submissions;

    @Column(columnDefinition = "TEXT COMMENT '标签(JSON数组)'")
    @Schema(description = "标签(JSON数组)", example = "[\"数组\",\"哈希表\"]")
    private String tags;

    @Column(columnDefinition = "LONGTEXT COMMENT '题目描述'")
    @Schema(description = "题目描述")
    private String description;

    @Column(columnDefinition = "LONGTEXT COMMENT '示例(JSON数组)'")
    @Schema(description = "示例(JSON数组)")
    private String examples;

    @Column(name = "default_code", columnDefinition = "LONGTEXT COMMENT '默认模板代码'")
    @Schema(description = "默认模板代码")
    private String defaultCode;

    @Column(name = "func_name", length = 64, columnDefinition = "VARCHAR(64) COMMENT '判题入口函数名，为空表示暂不支持在线判题'")
    @Schema(description = "判题入口函数名，为空表示暂不支持在线判题", example = "twoSum")
    private String funcName;

    @Column(columnDefinition = "LONGTEXT COMMENT '测试用例(JSON数组: input/expected/mode/accepts)'")
    @Schema(description = "测试用例(JSON数组)")
    private String testcases;

    @Column(name = "similar_ids", columnDefinition = "TEXT COMMENT '相似题目ID(JSON数组)'")
    @Schema(description = "相似题目ID(JSON数组)", example = "[15,3,53]")
    private String similarIds;

    @Column(columnDefinition = "LONGTEXT COMMENT '标准答案(JSON数组: 多解[{name,idea,code,complexity}])，仅供AI辅助编程参照，不返回公开接口'")
    @Schema(description = "标准答案(JSON数组: 多解[{name,idea,code,complexity}])，仅供AI辅助参照")
    private String solution;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否上架'")
    @Schema(description = "是否上架", example = "true")
    private Boolean enabled = true;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
