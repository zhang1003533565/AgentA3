package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_record")
public class MeetingRecord {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_TRANSCRIPTION = "transcription";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_session_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '会议主键ID'")
    private Long meetingSessionId;

    @Column(nullable = false, length = 40, columnDefinition = "VARCHAR(40) NOT NULL DEFAULT 'manual' COMMENT '记录来源'")
    private String source = SOURCE_MANUAL;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '会议记录内容'")
    private String content;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    private LocalDateTime createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_session_id", insertable = false, updatable = false)
    private MeetingSession session;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (source == null || source.isBlank()) {
            source = SOURCE_MANUAL;
        }
    }
}
