package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_question", indexes = {
        @Index(name = "idx_exam_question_type", columnList = "type"),
        @Index(name = "idx_exam_question_difficulty", columnList = "difficulty"),
        @Index(name = "idx_exam_question_created_by", columnList = "created_by")
})
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_question_id", length = 80, columnDefinition = "VARCHAR(80) COMMENT '智能体输出的题目ID'")
    private String sourceQuestionId;

    @Column(nullable = false, length = 40, columnDefinition = "VARCHAR(40) NOT NULL COMMENT '题型'")
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '题干'")
    private String stem;

    @Column(nullable = false, precision = 8, scale = 2, columnDefinition = "DECIMAL(8,2) NOT NULL COMMENT '分值'")
    private BigDecimal score;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL COMMENT '难度：easy/medium/hard'")
    private String difficulty;

    @Lob
    @Column(name = "knowledge_points_json", columnDefinition = "LONGTEXT COMMENT '知识点JSON数组'")
    private String knowledgePointsJson;

    @Lob
    @Column(name = "tags_json", columnDefinition = "LONGTEXT COMMENT '标签JSON数组'")
    private String tagsJson;

    @Lob
    @Column(name = "body_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL COMMENT '题型专属内容JSON'")
    private String bodyJson;

    @Lob
    @Column(name = "answer_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL COMMENT '标准答案JSON'")
    private String answerJson;

    @Column(columnDefinition = "TEXT COMMENT '解析'")
    private String analysis;

    @Lob
    @Column(name = "scoring_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL COMMENT '评分规则JSON'")
    private String scoringJson;

    @Lob
    @Column(name = "source_basis_json", columnDefinition = "LONGTEXT COMMENT '生成依据JSON数组'")
    private String sourceBasisJson;

    @Lob
    @Column(name = "raw_question_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL COMMENT '原始题目JSON快照'")
    private String rawQuestionJson;

    @Column(name = "source_agent", length = 80, columnDefinition = "VARCHAR(80) COMMENT '来源智能体'")
    private String sourceAgent;

    @Column(name = "source_title", length = 160, columnDefinition = "VARCHAR(160) COMMENT '来源标题或批次名称'")
    private String sourceTitle;

    @Column(name = "source_scene", length = 40, columnDefinition = "VARCHAR(40) COMMENT '来源场景：test/import/manual'")
    private String sourceScene;

    @Column(name = "created_by", columnDefinition = "BIGINT COMMENT '创建用户ID'")
    private Long createdBy;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-删除'")
    private Integer status = 1;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
