package com.example.appbackend.dto;

import lombok.Data;
import java.util.List;

public class PaperDTO {
    @Data public static class BankRequest { private String name; private Long subjectId; private String visibility = "private"; private String description; private String bankType; }
    @Data public static class QuestionRequest { private Long questionId; private Integer score; private String sourceType; private Long sourceId; private Integer questionOrder; }
    @Data public static class PaperRequest { private String name; private Long subjectId; private String subject; private String category; private String remark; private Integer duration; private Integer totalScore; }
    @Data public static class ScoreRequest { private Integer score; private Integer questionOrder; }
    @Data public static class QuestionVO {
        private Long id, bankId, subjectId, creatorId;
        private String subject, chapter, knowledgePoint, questionType, difficulty, content, options, answer, analysis, bankName;
        private Boolean favorited, selected;
        public QuestionVO() {}
    }
    @Data public static class BankVO {
        private Long id, subjectId, ownerId; private String name, visibility, description, bankType; private Long questionCount; private String updateTime;
    }
    @Data public static class PaperQuestionVO {
        private Long id, questionId, paperId; private Integer questionOrder, score; private String sourceType; private QuestionVO question;
    }
    @Data public static class PaperVO {
        private Long id, subjectId, creatorId; private String name, subject, category, remark, status, createTime, updateTime; private Integer duration, totalScore, questionCount; private List<PaperQuestionVO> questions;
    }
}
