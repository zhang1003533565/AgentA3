package com.example.appbackend.controller;

import com.example.appbackend.dto.UserResponse;
import com.example.appbackend.dto.LoginRequest;
import com.example.appbackend.dto.RegisterRequest;
import com.example.appbackend.dto.PasswordChangeRequest;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.service.UserService;
import com.example.appbackend.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录、注册、密码管理等认证相关接口")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Operation(summary = "用户注册", description = "注册新用户账号")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "注册成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或用户名已存在")
    })
    @PostMapping("/register")
    public Result<UserResponse> register(
            @Parameter(description = "注册信息", required = true) 
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return Result.success(response);
    }

    @Operation(summary = "APP端登录", description = "学生用户APP端登录，仅允许学生角色登录")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登录成功"),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
        @ApiResponse(responseCode = "403", description = "非学生角色不允许APP端登录")
    })
    @PostMapping("/applogin")
    public Result<UserResponse> applogin(
            @Parameter(description = "登录信息", required = true) 
            @Valid @RequestBody LoginRequest request) {
        UserResponse response = userService.applogin(request);
        return Result.success(response);
    }

    @Operation(summary = "Web端登录", description = "Web端用户登录，允许管理员或商家角色登录")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登录成功"),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
        @ApiResponse(responseCode = "403", description = "非管理员角色不允许Web端登录")
    })
    @PostMapping("/weblogin")
    public Result<UserResponse> weblogin(
            @Parameter(description = "登录信息", required = true) 
            @Valid @RequestBody LoginRequest request) {
        UserResponse response = userService.weblogin(request);
        return Result.success(response);
    }

    @Operation(summary = "获取当前用户信息", description = "根据用户ID获取当前登录用户信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "用户不存在")
    })
    @GetMapping("/current/{id}")
    public Result<UserResponse> getCurrentUser(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long id) {
        UserResponse response=userService.current(id);
        if(id==null){
            return Result.error(401,"用户不存在");
        }
        return Result.success(response);
    }

    @Operation(summary = "获取当前登录用户信息", description = "获取当前登录用户的信息（根据 token）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/current-user")
    public Result<UserResponse> getCurrentUserInfo(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        if (username == null) {
            return Result.error(401, "未登录");
        }
        User user = userService.findByUsername(username);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        String roleName = user.getRole() != null ? user.getRole().getName() : "STUDENT";
        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), roleName);
        UserResponse response = new UserResponse(token, user.getUsername(), roleName, user.getPhone(),
                user.getRealName(), user.getCollege(), user.getMajor(), user.getClassName(), user.getPersonalNumber(), user.getShareCode());
        return Result.success(response);
    }

    @Operation(summary = "修改密码", description = "修改当前登录用户的密码，需要登录状态")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "修改成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "400", description = "旧密码错误")
    })
    @PutMapping("/password")
    public Result<Void> changePassword(
            HttpServletRequest httpRequest, 
            @Parameter(description = "密码修改信息", required = true) 
            @Valid @RequestBody PasswordChangeRequest request) {
        String username = (String) httpRequest.getAttribute("username");
        userService.password(username, request);
        return Result.success();
    }

    @Operation(summary = "更新头像", description = "更新当前登录用户的头像")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @PutMapping("/avatar")
    public Result<Void> updateAvatar(
            HttpServletRequest httpRequest,
            @RequestBody java.util.Map<String, String> body) {
        String username = (String) httpRequest.getAttribute("username");
        if (username == null) {
            return Result.error(401, "未登录");
        }
        String avatarUrl = body.get("avatarUrl");
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return Result.error(400, "头像地址不能为空");
        }
        userService.updateAvatar(username, avatarUrl);
        return Result.success();
    }

}
