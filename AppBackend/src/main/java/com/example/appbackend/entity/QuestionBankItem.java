package com.example.appbackend.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity
@Table(name = "question_bank_item", uniqueConstraints = @UniqueConstraint(columnNames = {"bank_id", "question_id"}))
public class QuestionBankItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="bank_id", nullable=false) private Long bankId;
    @Column(name="question_id", nullable=false) private Long questionId;
    @Column(name="added_by", nullable=false) private Long addedBy;
    @Column(name="create_time") private LocalDateTime createTime;
    @PrePersist void create(){createTime=LocalDateTime.now();}
}
