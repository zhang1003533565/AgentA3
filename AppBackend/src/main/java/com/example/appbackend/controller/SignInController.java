package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signins")
@Tag(name = "签到管理", description = "签到接口")
public class SignInController {

    @Operation(summary = "签到", description = "学生签到")
    @PostMapping
    public Result<Void> signIn() {
        return Result.success();
    }

    @Operation(summary = "签到列表", description = "获取活动签到列表（教师/管理员）")
    @GetMapping("/activities/{activityId}/signins")
    public Result<Object> getSignIns(@PathVariable Long activityId) {
        return Result.success();
    }

    @Operation(summary = "补签", description = "补签（教师）")
    @PostMapping("/{id}/supplement")
    public Result<Void> supplementSignIn(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "检查签到状态", description = "检查签到状态（学生）")
    @GetMapping("/activities/{activityId}/signin-status")
    public Result<Object> getSignInStatus(@PathVariable Long activityId) {
        return Result.success();
    }
}
