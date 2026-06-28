package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_leader_message")
public class AiLeaderMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT 'Leader会话主键ID'")
    private Long leaderSessionId;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant'")
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '消息内容'")
    private String content;

    @Column(name = "answer_type", length = 40, columnDefinition = "VARCHAR(40) COMMENT '回答内容类型'")
    private String answerType;

    @Column(name = "output_type", length = 40, columnDefinition = "VARCHAR(40) COMMENT '前端展示主类型'")
    private String outputType;

    @Column(name = "agent_name", length = 80, columnDefinition = "VARCHAR(80) COMMENT '实际执行智能体'")
    private String agentName;

    @Column(name = "search_keyword", length = 160, columnDefinition = "VARCHAR(160) COMMENT 'AI整理出的检索关键词'")
    private String searchKeyword;

    @Lob
    @Column(name = "output_types_json", columnDefinition = "LONGTEXT COMMENT '前端展示类型JSON数组'")
    private String outputTypesJson;

    @Lob
    @Column(name = "output_meta_json", columnDefinition = "LONGTEXT COMMENT '输出展示元信息JSON'")
    private String outputMetaJson;

    @Lob
    @Column(name = "retrieval_meta_json", columnDefinition = "LONGTEXT COMMENT 'AI调用上下文和路由元信息JSON'")
    private String retrievalMetaJson;

    @Lob
    @Column(name = "trace_json", columnDefinition = "LONGTEXT COMMENT 'AI调用执行轨迹JSON数组'")
    private String traceJson;

    @Lob
    @Column(name = "attachments_json", columnDefinition = "LONGTEXT COMMENT '结构化附件JSON数组'")
    private String attachmentsJson;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_session_id", insertable = false, updatable = false)
    private AiLeaderSession session;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
