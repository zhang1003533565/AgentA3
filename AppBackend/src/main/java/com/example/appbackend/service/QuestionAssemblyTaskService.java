package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.TaskAccepted;
import com.example.appbackend.dto.QuestionAssemblyDTO.TaskView;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionAssemblyTaskService {

    TaskAccepted submit(AssemblyRequest request, MultipartFile file, Long userId, String authorization);

    TaskView get(String taskId, Long userId);
}
