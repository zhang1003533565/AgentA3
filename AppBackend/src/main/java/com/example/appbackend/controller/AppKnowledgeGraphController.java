package com.example.appbackend.controller;

import com.example.appbackend.dto.KnowledgeGraphDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PythonKnowledgeGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/learning/courses/python/knowledge-graph")
@Tag(name = "App Python 知识图谱", description = "叠加学生掌握证据的 Python 知识关系图")
public class AppKnowledgeGraphController {

    private final PythonKnowledgeGraphService knowledgeGraphService;

    public AppKnowledgeGraphController(PythonKnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @GetMapping
    @Operation(summary = "获取个人知识图谱")
    public Result<KnowledgeGraphDTO.GraphView> getGraph(HttpServletRequest request) {
        return Result.success(knowledgeGraphService.getGraph(requireUserId(request)));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute("userId");
        if (!(raw instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
