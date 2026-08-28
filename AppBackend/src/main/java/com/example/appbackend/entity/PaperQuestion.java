package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "paper_question", uniqueConstraints = @UniqueConstraint(columnNames = {"paper_id", "question_id"}))
public class PaperQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "paper_id", nullable = false) private Long paperId;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "question_order", nullable = false) private Integer questionOrder;
    @Column(nullable = false) private Integer score = 5;
    @Column(name = "source_type", length = 30) private String sourceType;
    @Column(name = "source_id") private Long sourceId;
    @Column(name = "create_time") private LocalDateTime createTime;
    @PrePersist void create() { createTime = LocalDateTime.now(); }
}
