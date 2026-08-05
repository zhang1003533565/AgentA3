package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "paper")
public class Paper {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "subject_id") private Long subjectId;
    @Column(length = 100) private String subject;
    @Column(length = 40, nullable = false) private String category;
    @Column(columnDefinition = "TEXT") private String remark;
    private Integer duration;
    @Column(name = "total_score") private Integer totalScore = 0;
    @Column(nullable = false, length = 20) private String status = "draft";
    @Column(name = "creator_id", nullable = false) private Long creatorId;
    @Column(name = "create_time") private LocalDateTime createTime;
    @Column(name = "update_time") private LocalDateTime updateTime;
    @PrePersist void create() { createTime = LocalDateTime.now(); updateTime = createTime; }
    @PreUpdate void update() { updateTime = LocalDateTime.now(); }
}
