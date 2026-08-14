package com.example.appbackend.service;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.ExamQuestionFolderDTO;
import com.example.appbackend.dto.PageResponse;

import java.util.List;

public interface ExamQuestionFolderService {

    List<ExamQuestionFolderDTO.FolderVO> listFolders(
            String visibility,
            Long viewerId,
            boolean admin,
            Long ownerUserId,
            String ownerKeyword);

    ExamQuestionFolderDTO.FolderVO createFolder(
            ExamQuestionFolderDTO.CreateRequest request,
            Long userId);

    ExamQuestionFolderDTO.FolderVO renameFolder(
            Long folderId,
            ExamQuestionFolderDTO.RenameRequest request,
            Long userId,
            boolean admin);

    ExamQuestionFolderDTO.FolderVO changeVisibility(
            Long folderId,
            ExamQuestionFolderDTO.VisibilityRequest request,
            Long userId,
            boolean admin);

    void deleteFolder(Long folderId, Long userId, boolean admin);

    ExamQuestionFolderDTO.FolderDetailVO getFolderDetail(Long folderId, Long userId, boolean admin);

    PageResponse<ExamQuestionDTO.QuestionVO> listFolderQuestions(
            Long folderId,
            Integer current,
            Integer size,
            Long userId,
            boolean admin);

    void addQuestion(Long folderId, Long questionId, Long userId, boolean admin);

    void removeQuestion(Long folderId, Long questionId, Long userId, boolean admin);

    ExamQuestionFolderDTO.PushQuestionsResult pushQuestions(
            Long sourceFolderId,
            ExamQuestionFolderDTO.PushQuestionsRequest request,
            Long userId,
            boolean admin);
}
