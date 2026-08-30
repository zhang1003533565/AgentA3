package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseAIGenerateDTO {
    @NotBlank
    private String prompt;
}
