package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "question_bank")
public class QuestionBank {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "subject_id") private Long subjectId;
    @Column(length = 30, nullable = false) private String visibility = "public";
    @Column(name = "owner_id") private Long ownerId;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "bank_type", length = 40) private String bankType;
    @Column(name = "create_time") private LocalDateTime createTime;
    @Column(name = "update_time") private LocalDateTime updateTime;
    @PrePersist void create() { createTime = LocalDateTime.now(); updateTime = createTime; }
    @PreUpdate void update() { updateTime = LocalDateTime.now(); }
}
