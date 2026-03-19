package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.ForumPost;

import java.util.List;

public interface PostService {

    PostResponse createPost(PostRequest request, Long userId);

    PostResponse updatePost(Long id, PostRequest request, Long userId);

    void deletePost(Long id, Long userId);

    PostResponse getPostDetail(Long id, Long currentUserId);

    PageResponse<PostListItem> getPostList(Integer pageNum, Integer pageSize, Long topicId, String keyword, String sortBy, Long userId, Long currentUserId);

    PageResponse<HotPostItem> getHotPosts(Integer pageNum, Integer pageSize);
}
