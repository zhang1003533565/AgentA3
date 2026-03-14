package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户更新请求")
public class UserUpdateRequest {

    @Schema(description = "真实姓名", example = "张三")
    @Size(max = 50, message = "真实姓名长度不能超过50")
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    @Schema(description = "邮箱", example = "test@campus.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "学院", example = "计算机学院")
    @Size(max = 100, message = "学院名称长度不能超过100")
    private String college;

    @Schema(description = "专业", example = "软件工程")
    @Size(max = 100, message = "专业名称长度不能超过100")
    private String major;

    @Schema(description = "班级", example = "软件21-1班")
    @Size(max = 50, message = "班级名称长度不能超过50")
    private String className;
}
