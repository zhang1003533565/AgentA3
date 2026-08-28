package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.service.CareerLearningProgressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/app/career-nebula/progress")
public class CareerLearningProgressController {

    private final CareerLearningProgressService service;

    public CareerLearningProgressController(CareerLearningProgressService service) {
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Object>> getProgress(HttpServletRequest request) {
        return Result.success(service.getProgress(requireUserId(request)));
    }

    @PutMapping("/items/{itemId}")
    public Result<Map<String, Object>> updateProgress(
            @PathVariable String itemId,
            @RequestBody UpdateProgressRequest body,
            HttpServletRequest request
    ) {
        return Result.success(service.updateProgress(
                requireUserId(request), body.careerId(), body.skillId(), itemId, body.completed()
        ));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("用户未登录");
    }

    public record UpdateProgressRequest(String careerId, String skillId, boolean completed) { }
}
