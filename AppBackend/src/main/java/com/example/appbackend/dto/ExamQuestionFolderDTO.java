package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ExamQuestionFolderDTO {

    private ExamQuestionFolderDTO() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank(message = "收藏夹名称不能为空")
        @Size(max = 80, message = "收藏夹名称最多 80 字符")
        private String name;

        @NotBlank(message = "可见范围不能为空")
        private String visibility;
    }

    @Data
    public static class RenameRequest {
        @NotBlank(message = "收藏夹名称不能为空")
        @Size(max = 80, message = "收藏夹名称最多 80 字符")
        private String name;
    }

    @Data
    public static class AddQuestionRequest {
        @NotNull(message = "题目ID不能为空")
        private Long questionId;
    }

    @Data
    public static class FolderVO {
        private Long id;
        private String name;
        private String visibility;
        private String visibilityLabel;
        private Long ownerUserId;
        private String ownerUsername;
        private String ownerPersonalNumber;
        private Long questionCount;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean ownedByCurrentUser;
    }

    @Data
    public static class FolderDetailVO extends FolderVO {
        private List<ExamQuestionDTO.QuestionVO> questions;
    }
}
