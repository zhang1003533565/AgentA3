package com.example.appbackend.service;

import com.example.appbackend.dto.DocumentConvertDTO;
import com.example.appbackend.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentConvertService {

    DocumentConvertDTO.TaskAccepted createTask(MultipartFile file, String convertType, Long userId, String authorization);

    DocumentConvertDTO.TaskView getTask(String taskId, Long userId);

    PageResponse<DocumentConvertDTO.TaskSummary> getHistory(Long userId, String convertType, int page, int size);

    byte[] downloadResult(String taskId, Long userId);

    void batchDelete(List<String> taskIds, Long userId);
}
