package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class LearningKnowledgeDTO {

    @Data
    @Schema(description = "课程知识检索请求")
    public static class RetrieveRequest {
        @NotBlank(message = "课程键不能为空")
        @Size(max = 32, message = "课程键最多 32 字符")
        private String courseKey;

        @NotBlank(message = "检索问题不能为空")
        @Size(max = 1000, message = "检索问题最多 1000 字符")
        private String query;

        @Min(value = 1, message = "召回数量至少为 1")
        @Max(value = 20, message = "召回数量最多为 20")
        private Integer topNumber;

        @DecimalMin(value = "0", message = "相似度不能小于 0")
        @DecimalMax(value = "2", message = "相似度不能大于 2")
        private Double similarity;

        @Size(max = 32, message = "检索模式最多 32 字符")
        private String searchMode;
    }

    @Data
    @Schema(description = "课程知识检索响应")
    public static class RetrieveResponse {
        private String courseKey;
        private List<Reference> references;

        @JsonIgnore
        @Schema(hidden = true)
        private Long accountId;

        @JsonIgnore
        @Schema(hidden = true)
        private String knowledgeId;

        @JsonIgnore
        @Schema(hidden = true)
        private Object raw;
    }

    @Data
    @Schema(description = "学生可见的知识引用")
    public static class Reference {
        private String id;
        private String title;
        private String documentName;
        private String content;
        private Double similarity;
        private String source;
    }
}
