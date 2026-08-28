package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "question")
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "bank_id", nullable = false) private Long bankId;
    @Column(name = "subject_id") private Long subjectId;
    @Column(length = 80) private String subject;
    @Column(length = 80) private String chapter;
    @Column(name = "knowledge_point", length = 120) private String knowledgePoint;
    @Column(name = "question_type", nullable = false, length = 30) private String questionType;
    @Column(length = 20) private String difficulty;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(columnDefinition = "TEXT") private String options;
    @Column(columnDefinition = "TEXT") private String answer;
    @Column(columnDefinition = "TEXT") private String analysis;
    @Column(name = "creator_id") private Long creatorId;
    @Column(name = "create_time") private LocalDateTime createTime;
    @Column(name = "update_time") private LocalDateTime updateTime;
    @PrePersist void create() { createTime = LocalDateTime.now(); updateTime = createTime; }
    @PreUpdate void update() { updateTime = LocalDateTime.now(); }
}
