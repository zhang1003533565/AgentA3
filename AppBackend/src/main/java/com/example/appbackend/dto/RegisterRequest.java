package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    @Schema(description = "用户名", example = "testuser")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    @Schema(description = "密码", example = "123456")
    private String password;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "test@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "角色(STUDENT/TEACHER)", example = "STUDENT")
    private String role;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "学院", example = "计算机学院")
    private String college;

    @Schema(description = "专业", example = "软件工程")
    private String major;

    @Schema(description = "班级", example = "软件21-1班")
    private String className;

    @Schema(description = "学号/工号", example = "2021001")
    private String personalNumber;

}
