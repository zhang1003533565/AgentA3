package com.example.appbackend.service;

import com.example.appbackend.dto.MaxKbKnowledgeDTO;
import com.example.appbackend.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface MaxKbKnowledgeService {

    List<MaxKbKnowledgeDTO.EnvironmentOption> listEnvironmentOptions();

    PageResponse<MaxKbKnowledgeDTO.AccountVO> listAccounts(
            Integer current,
            Integer size,
            String keyword,
            String environment,
            Integer status
    );

    MaxKbKnowledgeDTO.AccountVO createAccount(MaxKbKnowledgeDTO.AccountCreateRequest request);

    MaxKbKnowledgeDTO.AccountVO updateAccount(Long accountId, MaxKbKnowledgeDTO.AccountUpdateRequest request);

    void deleteAccount(Long accountId);

    MaxKbKnowledgeDTO.AccountVO updateAccountStatus(Long accountId, Integer status);

    Object testConnection(Long accountId);

    Object docs(Long accountId);

    Object listKnowledges(Long accountId, Map<String, String> queryParams);

    Object getKnowledge(Long accountId, String knowledgeId);

    Object listDocuments(Long accountId, String knowledgeId, Map<String, String> queryParams);

    Object uploadDocuments(
            Long accountId,
            String knowledgeId,
            List<MultipartFile> files,
            Integer limit,
            List<String> patterns,
            Boolean withFilter,
            String splitStrategy,
            String modelId
    );

    Object listParagraphs(Long accountId, String knowledgeId, String documentId, Map<String, String> queryParams);

    Object hitTest(Long accountId, Map<String, Object> request);
}
