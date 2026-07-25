package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyOptions;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.PrivateCommitResponse;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionAssemblyService {

    AssemblyOptions options(String authorization);

    AssemblyResponse generate(
            AssemblyRequest request, MultipartFile file, Long userId, String authorization);

    PrivateCommitResponse commitPrivate(String draftId, Long userId);
}
