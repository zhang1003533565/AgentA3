package com.example.appbackend.controller;

import com.example.appbackend.entity.PaperDictionary;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.PaperDictionaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api/papers/dictionaries")
public class PaperDictionaryController {
    private final PaperDictionaryService service;

    public PaperDictionaryController(PaperDictionaryService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<PaperDictionary>> list(@RequestParam String type, HttpServletRequest request) {
        return Result.success(service.list(type, user(request)));
    }

    @PostMapping
    public Result<PaperDictionary> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        Long userId = user(request);
        return Result.success("新增成功", service.create(body.type(), body.name(), userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = user(request);
        service.delete(id, userId);
        return Result.success("删除成功", null);
    }

    public record CreateRequest(String type, String name) {}

    private Long user(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        if (!(id instanceof Number)) {
            throw new com.example.appbackend.exception.BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return ((Number) id).longValue();
    }
}
