package com.example.appbackend.controller;

import com.example.appbackend.dto.MeetingDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetings")
@Tag(name = "会议空间", description = "App 会议创建、记录保存、会议智能体执行接口")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    @Operation(summary = "创建会议")
    public Result<MeetingDTO.SessionDetail> create(@Valid @RequestBody MeetingDTO.SessionRequest request,
                                                   HttpServletRequest httpRequest) {
        return Result.success(meetingService.createMeeting(currentUserId(httpRequest), request));
    }

    @PostMapping("/quick")
    @Operation(summary = "快速发起会议")
    public Result<MeetingDTO.SessionDetail> createQuick(@Valid @RequestBody MeetingDTO.QuickMeetingRequest request,
                                                        HttpServletRequest httpRequest) {
        return Result.success(meetingService.createQuickMeeting(currentUserId(httpRequest), request));
    }

    @PostMapping("/reservations")
    @Operation(summary = "预约会议")
    public Result<MeetingDTO.SessionDetail> reserve(@Valid @RequestBody MeetingDTO.ReserveMeetingRequest request,
                                                    HttpServletRequest httpRequest) {
        return Result.success(meetingService.reserveMeeting(currentUserId(httpRequest), request));
    }

    @PutMapping("/{sessionId}")
    @Operation(summary = "更新会议")
    public Result<MeetingDTO.SessionDetail> update(@PathVariable String sessionId,
                                                   @Valid @RequestBody MeetingDTO.SessionRequest request,
                                                   HttpServletRequest httpRequest) {
        return Result.success(meetingService.updateMeeting(currentUserId(httpRequest), sessionId, request));
    }

    @GetMapping
    @Operation(summary = "我的会议列表")
    public Result<PageResponse<MeetingDTO.SessionItem>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                             @RequestParam(defaultValue = "20") Integer pageSize,
                                                             @RequestParam(required = false) String keyword,
                                                             HttpServletRequest httpRequest) {
        return Result.success(meetingService.listMeetings(currentUserId(httpRequest), pageNum, pageSize, keyword));
    }

    @PostMapping("/join")
    @Operation(summary = "通过会议号加入会议")
    public Result<MeetingDTO.SessionDetail> join(@Valid @RequestBody MeetingDTO.JoinRoomRequest request,
                                                 HttpServletRequest httpRequest) {
        return Result.success(meetingService.joinMeeting(currentUserId(httpRequest), request));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "会议详情")
    public Result<MeetingDTO.SessionDetail> detail(@PathVariable String sessionId,
                                                   HttpServletRequest httpRequest) {
        return Result.success(meetingService.getMeeting(currentUserId(httpRequest), sessionId));
    }

    @PostMapping("/{sessionId}/start")
    @Operation(summary = "开始会议")
    public Result<MeetingDTO.SessionDetail> start(@PathVariable String sessionId,
                                                  HttpServletRequest httpRequest) {
        return Result.success(meetingService.startMeeting(currentUserId(httpRequest), sessionId));
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "结束会议")
    public Result<MeetingDTO.SessionDetail> end(@PathVariable String sessionId,
                                                HttpServletRequest httpRequest) {
        return Result.success(meetingService.endMeeting(currentUserId(httpRequest), sessionId));
    }

    @PostMapping("/{sessionId}/records")
    @Operation(summary = "保存会议记录")
    public Result<MeetingDTO.RecordItem> addRecord(@PathVariable String sessionId,
                                                   @Valid @RequestBody MeetingDTO.RecordRequest request,
                                                   HttpServletRequest httpRequest) {
        return Result.success(meetingService.addRecord(currentUserId(httpRequest), sessionId, request));
    }

    @PostMapping("/{sessionId}/agents/run")
    @Operation(summary = "运行会议智能体")
    public Result<MeetingDTO.RunAgentResponse> runAgent(@PathVariable String sessionId,
                                                        @Valid @RequestBody MeetingDTO.RunAgentRequest request,
                                                        HttpServletRequest httpRequest) {
        return Result.success(meetingService.runAgent(
                currentUserId(httpRequest),
                sessionId,
                request,
                httpRequest.getHeader("Authorization")
        ));
    }

    @PostMapping("/{sessionId}/agents/preview")
    @Operation(summary = "预览会议智能体输出", description = "用于实时总结等轻量场景，不保存会议记录和智能体结果")
    public Result<MeetingDTO.RunAgentResponse> previewAgent(@PathVariable String sessionId,
                                                            @Valid @RequestBody MeetingDTO.RunAgentRequest request,
                                                            HttpServletRequest httpRequest) {
        return Result.success(meetingService.previewAgent(
                currentUserId(httpRequest),
                sessionId,
                request,
                httpRequest.getHeader("Authorization")
        ));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }
}
