package com.example.appbackend.controller;

import com.example.appbackend.dto.UserResponse;
import com.example.appbackend.dto.LoginRequest;
import com.example.appbackend.dto.RegisterRequest;
import com.example.appbackend.dto.PasswordChangeRequest;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录、注册、密码管理接口")
public class AuthController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户注册", description = "注册新用户")
    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return Result.success(response);
    }

    @Operation(summary = "APP端登录", description = "学生用户APP端登录（仅允许学生角色）")
    @PostMapping("/applogin")
    public Result<UserResponse> applogin(@Valid @RequestBody LoginRequest request) {
        UserResponse response = userService.applogin(request);
        return Result.success(response);
    }

    @Operation(summary = "Web端登录", description = "Web端用户登录（支持所有角色）")
    @PostMapping("/weblogin")
    public Result<UserResponse> weblogin(@Valid @RequestBody LoginRequest request) {
        UserResponse response = userService.login(request);
        return Result.success(response);
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户信息")
    @GetMapping("/current/{id}")
    public Result<UserResponse> getCurrentUser(@PathVariable Long id) {
        UserResponse response=userService.current(id);
        if(id==null){
            return Result.error("用户不存在");
        }
        return Result.success(response);
    }

    @Operation(summary = "修改密码", description = "修改当前用户密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return Result.success();
    }

}
