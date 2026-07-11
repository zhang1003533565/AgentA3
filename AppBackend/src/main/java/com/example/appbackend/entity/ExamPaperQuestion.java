package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "exam_paper_question", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exam_paper_question", columnNames = {"paper_id", "question_id"}),
        @UniqueConstraint(name = "uk_exam_paper_sort_order", columnNames = {"paper_id", "sort_order"})
}, indexes = {
        @Index(name = "idx_exam_paper_question_paper", columnList = "paper_id")
})
public class ExamPaperQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "section_order")
    private Integer sectionOrder;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal score;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT NOT NULL")
    private String stem;

    @Lob
    @Column(name = "body_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL")
    private String bodyJson;

    @Lob
    @Column(name = "answer_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL")
    private String answerJson;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Lob
    @Column(name = "scoring_json", nullable = false, columnDefinition = "LONGTEXT NOT NULL")
    private String scoringJson;
}
