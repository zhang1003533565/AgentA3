package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "话题创建/更新请求")
public class TopicRequest {

    @NotBlank(message = "话题名称不能为空")
    @Size(max = 50, message = "话题名称最多50字符")
    @Schema(description = "话题名称", example = "考研经验")
    private String topicName;

    @Size(max = 255, message = "话题图标URL最多255字符")
    @Schema(description = "话题图标URL", example = "https://example.com/icon.png")
    private String topicIcon;

    @Size(max = 200, message = "话题描述最多200字符")
    @Schema(description = "话题描述", example = "分享考研经验和学习方法")
    private String description;

    @Schema(description = "是否热门: 0-否, 1-是", example = "0")
    private Integer isHot;

    @Schema(description = "话题状态: ACTIVE-启用, INACTIVE-禁用", example = "ACTIVE")
    private String status;
}
