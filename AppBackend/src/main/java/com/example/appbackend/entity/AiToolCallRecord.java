package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_tool_call_record")
public class AiToolCallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_session_id", columnDefinition = "BIGINT COMMENT 'Leader会话主键ID'")
    private Long leaderSessionId;

    @Column(name = "message_id", columnDefinition = "BIGINT COMMENT '助手消息主键ID'")
    private Long messageId;

    @Column(name = "user_id", columnDefinition = "BIGINT COMMENT '用户ID'")
    private Long userId;

    @Column(name = "tool_name", length = 120, columnDefinition = "VARCHAR(120) COMMENT '被调用的工具名称'")
    private String toolName;

    @Column(name = "tool_display_name", length = 200, columnDefinition = "VARCHAR(200) COMMENT '工具显示名称'")
    private String toolDisplayName;

    @Column(name = "user_input", length = 500, columnDefinition = "VARCHAR(500) COMMENT '用户输入摘要'")
    private String userInput;

    @Lob
    @Column(name = "candidate_tools_json", columnDefinition = "LONGTEXT COMMENT '候选工具打分JSON数组'")
    private String candidateToolsJson;

    @Column(name = "intent", length = 80, columnDefinition = "VARCHAR(80) COMMENT '意图识别结果'")
    private String intent;

    @Column(name = "tool_called", columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否调用了工具：0否1是'")
    private Boolean toolCalled;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
