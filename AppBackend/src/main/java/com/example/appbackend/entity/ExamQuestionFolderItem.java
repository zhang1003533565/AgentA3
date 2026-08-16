package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_question_folder_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_eqfi_folder_question", columnNames = {"folder_id", "question_id"})
}, indexes = {
        @Index(name = "idx_eqfi_folder", columnList = "folder_id"),
        @Index(name = "idx_eqfi_question", columnList = "question_id")
})
public class ExamQuestionFolderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '收藏夹ID'")
    private Long folderId;

    @Column(name = "question_id", nullable = false, columnDefinition = "BIGINT NOT NULL COMMENT '题目ID'")
    private Long questionId;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '加入时间'")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
