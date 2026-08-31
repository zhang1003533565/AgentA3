package com.example.appbackend.service;

import com.example.appbackend.dto.ActivityAgentGenerateRequest;
import com.example.appbackend.dto.ActivityAgentGenerateResponse;

/**
 * AI 活动草稿生成服务：调用 activity_publish_agent 生成/补全活动草稿。
 * 只负责生成草稿，不创建活动、不落库。
 */
public interface ActivityAgentService {

    ActivityAgentGenerateResponse generate(ActivityAgentGenerateRequest request, String authorization);
}
