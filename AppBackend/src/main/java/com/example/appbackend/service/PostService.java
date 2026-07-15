package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.ForumPost;

import java.util.List;

public interface PostService {

    PostResponse createPost(PostRequest request, Long userId);

    PostResponse updatePost(Long id, PostRequest request, Long userId);

    void deletePost(Long id, Long userId);

    void deletePostByAdmin(Long id);

    void batchDeletePosts(List<Long> ids);

    PostResponse getPostDetail(Long id, Long currentUserId);

    PageResponse<PostListItem> getPostList(Integer pageNum, Integer pageSize, Long topicId, String keyword, String sortBy, Long userId, String status, Long currentUserId);

    PageResponse<HotPostItem> getHotPosts(Integer pageNum, Integer pageSize);

    PageResponse<UserPostResponse> getUserPost(Long userId, Integer pageNum, Integer pageSize);

    PageResponse<UserLikeResponse> getUserLikes(Long userId, Integer pageNum, Integer pageSize);

    PostListItem toPostListItem(ForumPost post, Long currentUserId);

    void batchDeletePostsByAdmin(List<Long> ids);

    void togglePin(Long id);

    void toggleHighlight(Long id);

    void toggleHidden(Long id);

    PageResponse<PostListItem> getAdminPostList(Integer pageNum, Integer pageSize, String keyword, String status, String sortBy, Long topicId);
}
