package com.example.appbackend.controller;

import com.example.appbackend.dto.MindMapDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MindMapService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/ai/mindmap")
public class AiMindMapController {
    private final MindMapService mindMapService;

    public AiMindMapController(MindMapService mindMapService) {
        this.mindMapService = mindMapService;
    }

    @PostMapping("/generate")
    public Result<MindMapDTO.GenerateResponse> generate(@Valid @RequestBody MindMapDTO.GenerateRequest body,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                                        HttpServletRequest request) {
        return Result.success(mindMapService.generate(requireUserId(request), body, authorization));
    }

    @PostMapping("/optimize")
    public Result<MindMapDTO.GenerateResponse> optimize(@Valid @RequestBody MindMapDTO.OptimizeRequest body,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                                        HttpServletRequest request) {
        return Result.success(mindMapService.optimize(requireUserId(request), body, authorization));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MindMapDTO.UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        return Result.success(mindMapService.uploadAndParse(requireUserId(request), file));
    }

    @GetMapping("/history")
    public Result<List<MindMapDTO.HistoryItem>> history(HttpServletRequest request) {
        return Result.success(mindMapService.history(requireUserId(request)));
    }

    @GetMapping("/{id}")
    public Result<MindMapDTO.GenerateResponse> detail(@PathVariable String id, HttpServletRequest request) {
        return Result.success(mindMapService.detail(requireUserId(request), id));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute("userId");
        if (!(raw instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
