package com.example.appbackend.controller;

import com.example.appbackend.dto.ForumReportStatisticsResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.CommentService;
import com.example.appbackend.service.ForumReportService;
import com.example.appbackend.service.PostService;
import com.example.appbackend.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 论坛统计接口 — 供管理后台统计卡片使用
 */
@RestController
@RequestMapping("/api/forum/statistics")
public class ForumStatisticsController {

    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private TopicService topicService;
    @Autowired private ForumReportService reportService;

    /**
     * 返回论坛全局统计数据
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 帖子统计
        data.put("totalPosts", postService.countAllPosts());
        data.put("publishedPosts", postService.countByStatus("PUBLISHED"));
        data.put("hiddenPosts", postService.countByStatus("HIDDEN"));
        data.put("deletedPosts", postService.countByStatus("DELETED"));

        // 评论统计
        data.put("totalComments", commentService.countAllComments());
        data.put("normalComments", commentService.countByStatus("NORMAL"));
        data.put("hiddenComments", commentService.countByStatus("HIDDEN"));

        // 话题统计
        data.put("totalTopics", topicService.countAllTopics());
        data.put("activeTopics", topicService.countByStatus("ACTIVE"));
        data.put("hotTopics", topicService.countHotTopics());

        // 举报统计
        ForumReportStatisticsResponse reportStats = reportService.getStatistics();
        data.put("totalReports", reportStats.getTotal());
        data.put("pendingReports", reportStats.getPending());
        data.put("handledReports", reportStats.getHandled());
        data.put("ignoredReports", reportStats.getRejected());

        return Result.success(data);
    }
}
