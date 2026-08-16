package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AssistantResourceInteractionRequest {

    @NotBlank(message = "资源互动动作不能为空")
    @Pattern(
            regexp = "view|open|download|preview|follow_up|dismiss|complete",
            message = "资源互动动作不受支持"
    )
    private String action;
}
