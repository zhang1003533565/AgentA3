package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户响应")
public class UserResponse {

    @Schema(description = "JWT令牌")
    private String token;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "手机号")
    private String phone;
}
