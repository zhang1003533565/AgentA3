package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeChatDTO;
import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CourseKnowledgeService;
import com.example.appbackend.service.KnowledgeChatService;
import com.example.appbackend.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class CourseKnowledgeServiceImpl implements CourseKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(CourseKnowledgeServiceImpl.class);
    private static final String PYTHON_COURSE_KEY = "python";
    private static final String PYTHON_ENABLED_KEY = "ai.learning.python.enabled";
    private static final String PYTHON_ACCOUNT_ID_KEY = "ai.learning.python.maxkb.account-id";
    private static final String PYTHON_KNOWLEDGE_ID_KEY = "ai.learning.python.maxkb.knowledge-id";
    private static final int MAX_REFERENCE_CONTENT_LENGTH = 1_200;
    private static final int RETRIEVAL_FAILURE_CODE = 502;
    private static final String RETRIEVAL_FAILURE_MESSAGE = "课程知识检索暂时不可用";

    private final KnowledgeChatService knowledgeChatService;
    private final SystemConfigService systemConfigService;

    public CourseKnowledgeServiceImpl(
            KnowledgeChatService knowledgeChatService,
            SystemConfigService systemConfigService
    ) {
        this.knowledgeChatService = knowledgeChatService;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public LearningKnowledgeDTO.RetrieveResponse retrieve(LearningKnowledgeDTO.RetrieveRequest request) {
        if (request == null || !PYTHON_COURSE_KEY.equals(request.getCourseKey())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "不支持的课程知识库");
        }
        if (!Boolean.TRUE.equals(systemConfigService.getBooleanValue(PYTHON_ENABLED_KEY, false))) {
            throw new BusinessException(503, "Python 课程知识库未启用");
        }

        Long accountId = systemConfigService.getLongValue(PYTHON_ACCOUNT_ID_KEY, null);
        String knowledgeId = systemConfigService.getValue(PYTHON_KNOWLEDGE_ID_KEY, null);
        if (accountId == null || !StringUtils.hasText(knowledgeId)) {
            throw new BusinessException(503, "Python 课程知识库未配置");
        }

        KnowledgeChatDTO.RetrievalRequest retrievalRequest = new KnowledgeChatDTO.RetrievalRequest();
        retrievalRequest.setAccountId(accountId);
        retrievalRequest.setKnowledgeId(knowledgeId.trim());
        retrievalRequest.setQuery(request.getQuery());
        retrievalRequest.setTopNumber(request.getTopNumber());
        retrievalRequest.setSimilarity(request.getSimilarity());
        retrievalRequest.setSearchMode(request.getSearchMode());
        KnowledgeChatDTO.RetrievalResult retrievalResult;
        String requestId = UUID.randomUUID().toString();
        try {
            retrievalResult = knowledgeChatService.retrieve(retrievalRequest);
        } catch (RuntimeException error) {
            log.warn("Course retrieval failed: requestId={}, courseKey={}, exceptionType={}",
                    requestId, PYTHON_COURSE_KEY, error.getClass().getName());
            throw new BusinessException(RETRIEVAL_FAILURE_CODE, RETRIEVAL_FAILURE_MESSAGE);
        }

        List<KnowledgeChatDTO.Reference> internalReferences = retrievalResult == null
                || retrievalResult.getReferences() == null
                ? List.of()
                : retrievalResult.getReferences();
        LearningKnowledgeDTO.RetrieveResponse response = new LearningKnowledgeDTO.RetrieveResponse();
        response.setCourseKey(PYTHON_COURSE_KEY);
        response.setReferences(internalReferences.stream().map(this::sanitizeReference).toList());
        return response;
    }

    private LearningKnowledgeDTO.Reference sanitizeReference(KnowledgeChatDTO.Reference source) {
        LearningKnowledgeDTO.Reference target = new LearningKnowledgeDTO.Reference();
        target.setId(source.getId());
        target.setTitle(source.getTitle());
        target.setDocumentName(source.getDocumentName());
        target.setContent(truncate(source.getContent()));
        target.setSimilarity(source.getSimilarity());
        target.setSource(source.getSource());
        return target;
    }

    private String truncate(String content) {
        if (content == null || content.length() <= MAX_REFERENCE_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_REFERENCE_CONTENT_LENGTH);
    }
}
