package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.util.List;

public interface CommentService {

    CommentResponse createComment(CommentRequest request, Long userId);

    void deleteComment(Long id, Long userId);

    PageResponse<CommentResponse> getCommentList(Long postId, Integer pageNum, Integer pageSize, Long currentUserId);

    PageResponse<CommentResponse> getAdminCommentList(Long postId, String keyword, String status, Integer pageNum, Integer pageSize);

    CommentResponse getCommentDetail(Long id, Long currentUserId);

    void deleteCommentByAdmin(Long id);

    void batchDeleteComments(List<Long> ids);
}
