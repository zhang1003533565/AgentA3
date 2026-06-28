package com.example.appbackend.controller;

import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "用户画像", description = "个人画像雷达图、证据沉淀和更新规则")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/radar/my")
    @Operation(summary = "我的画像雷达图快照")
    public Result<UserProfileDTO.RadarSnapshot> myRadar(HttpServletRequest request,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(userProfileService.getSnapshot(currentUserId(request), authorization));
    }

    @GetMapping("/rules")
    @Operation(summary = "画像规则与 Leader 使用策略")
    public Result<UserProfileDTO.AdminRulesResponse> rules(HttpServletRequest request) {
        requireLogin(request);
        return Result.success(userProfileService.getRules());
    }

    @PostMapping("/evidence")
    @Operation(summary = "提交画像证据", description = "聊天、会议、做题和点击行为只先提交证据，由画像服务按规则慢更新")
    public Result<UserProfileDTO.EvidenceResponse> addEvidence(@Valid @RequestBody UserProfileDTO.EvidenceRequest evidence,
                                                               HttpServletRequest request) {
        return Result.success(userProfileService.addEvidence(currentUserId(request), evidence));
    }

    private void requireLogin(HttpServletRequest request) {
        currentUserId(request);
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }
}
