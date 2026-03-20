package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "话题响应")
public class TopicResponse {

    @Schema(description = "话题ID", example = "1")
    private Long id;

    @Schema(description = "话题名称", example = "考研经验")
    private String topicName;

    @Schema(description = "话题图标URL", example = "https://example.com/icon.png")
    private String topicIcon;

    @Schema(description = "话题描述", example = "分享考研经验和学习方法")
    private String description;

    @Schema(description = "帖子数量", example = "100")
    private Integer postCount;

    @Schema(description = "是否热门: 0-否, 1-是", example = "1")
    private Integer isHot;

    @Schema(description = "话题状态: ACTIVE-启用, INACTIVE-禁用", example = "ACTIVE")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
