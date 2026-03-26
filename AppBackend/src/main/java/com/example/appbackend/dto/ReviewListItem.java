package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评价列表项")
public class ReviewListItem {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "设施ID")
    private Long facilityId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "用户头像")
    private String userAvatar;

    @Schema(description = "评分")
    private Integer score;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "图片列表")
    private String images;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
