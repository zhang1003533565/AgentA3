package com.example.appbackend.controller;

import com.example.appbackend.dto.FlowchartDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.FlowchartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ai/flowchart")
public class AiFlowchartController {
    private final FlowchartService flowchartService;

    public AiFlowchartController(FlowchartService flowchartService) {
        this.flowchartService = flowchartService;
    }

    @PostMapping("/generate")
    public Result<FlowchartDTO.GenerateResponse> generate(@Valid @RequestBody FlowchartDTO.GenerateRequest body,
                                                           @RequestHeader(value = "Authorization", required = false) String authorization,
                                                           HttpServletRequest request) {
        return Result.success(flowchartService.generate(requireUserId(request), body, authorization));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FlowchartDTO.UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                       HttpServletRequest request) {
        return Result.success(flowchartService.uploadAndParse(requireUserId(request), file));
    }

    @GetMapping("/history")
    public Result<List<FlowchartDTO.HistoryItem>> history(HttpServletRequest request) {
        return Result.success(flowchartService.history(requireUserId(request)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id, HttpServletRequest request) {
        flowchartService.delete(requireUserId(request), id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<FlowchartDTO.GenerateResponse> detail(@PathVariable String id, HttpServletRequest request) {
        return Result.success(flowchartService.detail(requireUserId(request), id));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute("userId");
        if (!(raw instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
