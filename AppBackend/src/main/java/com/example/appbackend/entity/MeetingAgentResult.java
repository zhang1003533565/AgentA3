package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_agent_result")
public class MeetingAgentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '会议主键ID'")
    private Long meetingSessionId;

    @Column(name = "agent_name", nullable = false, length = 80, columnDefinition = "VARCHAR(80) NOT NULL COMMENT '会议智能体名称'")
    private String agentName;

    @Column(name = "answer_type", length = 40, columnDefinition = "VARCHAR(40) COMMENT '回答类型'")
    private String answerType;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '智能体输出内容'")
    private String answer;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_session_id", insertable = false, updatable = false)
    private MeetingSession session;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
