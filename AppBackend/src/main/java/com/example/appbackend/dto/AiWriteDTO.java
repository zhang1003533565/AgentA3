package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AiWriteDTO {

    @Data
    public static class WriteRequest {
        @NotBlank(message = "写作需求不能为空")
        private String prompt;

        private String tone;

        private String wordCount;

        private String modelName;
    }

    @Data
    public static class WriteResponse {
        private String content;
        private String model;
    }
}
