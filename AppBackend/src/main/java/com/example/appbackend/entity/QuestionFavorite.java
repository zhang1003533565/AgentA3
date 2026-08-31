package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "question_favorite", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "question_id"}))
public class QuestionFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "create_time") private LocalDateTime createTime;
    @PrePersist void create() { createTime = LocalDateTime.now(); }
}
