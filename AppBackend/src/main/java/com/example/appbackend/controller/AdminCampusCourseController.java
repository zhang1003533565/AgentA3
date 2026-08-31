package com.example.appbackend.controller;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.dto.CourseAIGenerateDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CampusCourseService;
import com.example.appbackend.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/campus-courses")
public class AdminCampusCourseController {
    private static final Logger log = LoggerFactory.getLogger(AdminCampusCourseController.class);
    private static final String AI_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String AI_API_KEY = "sk-ws-H.EPYRPPL.1JmV.MEUCIQCKbGSoYRNDuijfOWZVyT67wkKEOoY5jb3LNDed9B5NcQIgAXA8SskzqdKN0Zrp4bntl40Cl9Xmr0UNSVzUEUTV9xw";
    private static final String AI_MODEL = "qwen3.7-max";

    private final CampusCourseService courseService;
    private final SystemConfigService systemConfigService;

    public AdminCampusCourseController(CampusCourseService courseService, SystemConfigService systemConfigService) {
        this.courseService = courseService;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        adminId(request);
        return Result.success(courseService.adminList());
    }

    @GetMapping("/{courseId}")
    public Result<?> detail(@PathVariable Long courseId, HttpServletRequest request) {
        adminId(request);
        return Result.success(courseService.adminDetail(courseId));
    }

    @GetMapping("/types")
    public Result<?> listTypes(HttpServletRequest request) {
        adminId(request);
        return Result.success(courseService.listCourseTypes());
    }

    @PostMapping("/types")
    public Result<?> createType(@Valid @RequestBody CampusCourseDTO.CourseTypeSaveRequest body,
                                HttpServletRequest request) {
        adminId(request);
        return Result.success("课程类型已创建", courseService.createCourseType(body));
    }

    @DeleteMapping("/types/{typeCode}")
    public Result<Void> deleteType(@PathVariable String typeCode, HttpServletRequest request) {
        adminId(request);
        courseService.deleteCourseType(typeCode);
        return Result.success("课程分类已删除", null);
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody CampusCourseDTO.SaveRequest body,
                            HttpServletRequest request) {
        return Result.success("课程已创建", courseService.create(body, adminId(request)));
    }

    @PutMapping("/{courseId}")
    public Result<?> update(@PathVariable Long courseId,
                            @Valid @RequestBody CampusCourseDTO.SaveRequest body,
                            HttpServletRequest request) {
        adminId(request);
        return Result.success("课程已保存", courseService.update(courseId, body));
    }

    @PostMapping("/{courseId}/publish")
    public Result<?> publish(@PathVariable Long courseId, HttpServletRequest request) {
        return Result.success("课程已发布", courseService.publish(courseId, adminId(request)));
    }

    @PostMapping("/{courseId}/offline")
    public Result<?> offline(@PathVariable Long courseId, HttpServletRequest request) {
        adminId(request);
        return Result.success("课程已下架", courseService.offline(courseId));
    }

    @DeleteMapping("/{courseId}")
    public Result<Void> delete(@PathVariable Long courseId, HttpServletRequest request) {
        adminId(request);
        courseService.delete(courseId);
        return Result.success("课程已删除", null);
    }

    @PostMapping("/{courseId}/chapters")
    public Result<?> createChapter(@PathVariable Long courseId,
                                   @Valid @RequestBody CampusCourseDTO.ChapterSaveRequest body,
                                   HttpServletRequest request) {
        adminId(request);
        return Result.success("章节已添加", courseService.createChapter(courseId, body));
    }

    @PostMapping("/{courseId}/ai/generate")
    public Result<?> generateChapterContent(@PathVariable Long courseId,
                                           @Valid @RequestBody CourseAIGenerateDTO body,
                                           HttpServletRequest request) {
        adminId(request);

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(120000);
            factory.setReadTimeout(120000);

            RestTemplate restTemplate = new RestTemplate(factory);
            Map<String, Object> payload = Map.of("prompt", body.getPrompt());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AI-Base-Url", AI_BASE_URL);
            headers.set("X-AI-Api-Key", AI_API_KEY);
            headers.set("X-AI-Model", AI_MODEL);

            log.info("AI request headers prepared: X-AI-Base-Url={}, X-AI-Api-KeyPresent={}, X-AI-Model={}",
                    headers.getFirst("X-AI-Base-Url"),
                    headers.getFirst("X-AI-Api-Key") != null && !headers.getFirst("X-AI-Api-Key").isEmpty(),
                    headers.getFirst("X-AI-Model"));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            Map<String, Object> aiResponse = restTemplate.postForObject(
                    "http://localhost:8081/generate",
                    requestEntity,
                    Map.class
            );

            if (aiResponse == null) {
                throw new BusinessException(500, "AI 生成失败，返回为空");
            }

            Object sectionsObj = aiResponse.get("sections");
            if (!(sectionsObj instanceof List<?> rawSections)) {
                throw new BusinessException(500, "AI 生成失败，sections 数据格式异常");
            }

            List<Map<String, Object>> sections = new ArrayList<>();
            for (Object item : rawSections) {
                if (item instanceof Map<?, ?> sectionMap) {
                    Map<String, Object> normalizedSection = new java.util.HashMap<>();
                    sectionMap.forEach((key, value) -> normalizedSection.put(String.valueOf(key), value));
                    sections.add(normalizedSection);
                }
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("chapterTitle", aiResponse.getOrDefault("chapterTitle", "AI 生成章节"));
            result.put("estimatedMinutes", aiResponse.getOrDefault("estimated_minutes", 0));
            result.put("sections", sections);

            return Result.success("章节已生成", result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "AI 生成失败，请稍后重试: " + e.getMessage());
        }
    }

    @PutMapping("/{courseId}/chapters/{chapterId}")
    public Result<?> updateChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
                                   @Valid @RequestBody CampusCourseDTO.ChapterSaveRequest body,
                                   HttpServletRequest request) {
        adminId(request);
        return Result.success("章节已保存", courseService.updateChapter(courseId, chapterId, body));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public Result<Void> deleteChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
                                      HttpServletRequest request) {
        adminId(request);
        courseService.deleteChapter(courseId, chapterId);
        return Result.success("章节已删除", null);
    }

    @PostMapping("/{courseId}/exams")
    public Result<?> linkExam(@PathVariable Long courseId,
                              @Valid @RequestBody CampusCourseDTO.ExamLinkRequest body,
                              HttpServletRequest request) {
        return Result.success("考试已关联", courseService.linkExam(courseId, body, adminId(request)));
    }

    @DeleteMapping("/{courseId}/exams/{linkId}")
    public Result<Void> unlinkExam(@PathVariable Long courseId, @PathVariable Long linkId,
                                   HttpServletRequest request) {
        adminId(request);
        courseService.unlinkExam(courseId, linkId);
        return Result.success("考试关联已移除", null);
    }

    private Long adminId(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(403, "仅管理员可管理校园课程");
        }
        Object userId = request.getAttribute("userId");
        if (!(userId instanceof Long id)) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }
}
