package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "关注状态响应")
public class FollowStatusResponse {

    @Schema(description = "当前用户是否关注了该用户", example = "true")
    private Boolean following;

    @Schema(description = "该用户的粉丝数", example = "100")
    private Long followerCount;

    @Schema(description = "该用户的关注数", example = "50")
    private Long followingCount;

    public FollowStatusResponse(Boolean following, Long followerCount) {
        this.following = following;
        this.followerCount = followerCount;
        this.followingCount = null;
    }
}
