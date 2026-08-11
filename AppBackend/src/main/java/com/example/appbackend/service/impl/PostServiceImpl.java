package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.ForumLike;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.entity.ForumTopic;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumCommentRepository;
import com.example.appbackend.repository.ForumFavoriteRepository;
import com.example.appbackend.repository.ForumLikeRepository;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.repository.ForumTopicRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.PostService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PostServiceImpl implements PostService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_HIDDEN = "HIDDEN";

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumLikeRepository likeRepository;

    @Autowired
    private ForumFavoriteRepository favoriteRepository;

    @Autowired
    private ForumCommentRepository commentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PostResponse createPost(PostRequest request, Long userId) {
        ensureTopicExists(request.getTopicId());
        ForumPost post = new ForumPost();
        post.setUserId(userId);
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(serializeImages(request.getImages()));
        post.setTopicId(request.getTopicId());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(STATUS_PUBLISHED);
        post.setPinOrder(0);
        post.setHighlighted(false);

        ForumPost savedPost = postRepository.save(post);
        if (savedPost.getTopicId() != null) {
            topicRepository.incrementPostCount(savedPost.getTopicId());
        }
        return toPostResponse(savedPost, userId);
    }

    @Override
    public PostResponse updatePost(Long id, PostRequest request, Long userId) {
        ForumPost post = getVisiblePost(id);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权编辑此帖子");
        }

        Long oldTopicId = post.getTopicId();
        ensureTopicExists(request.getTopicId());

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(serializeImages(request.getImages()));
        post.setTopicId(request.getTopicId());

        ForumPost updatedPost = postRepository.save(post);
        if (oldTopicId != null && !oldTopicId.equals(request.getTopicId())) {
            topicRepository.decrementPostCount(oldTopicId);
            if (request.getTopicId() != null) {
                topicRepository.incrementPostCount(request.getTopicId());
            }
        }
        return toPostResponse(updatedPost, userId);
    }

    @Override
    public void deletePost(Long id, Long userId) {
        ForumPost post = getVisiblePost(id);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此帖子");
        }
        softDeletePost(post);
    }

    @Override
    public void deletePostByAdmin(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        softDeletePost(post);
    }

    @Override
    public void batchDeletePosts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> postRepository.findById(id).ifPresent(this::softDeletePost));
    }

    @Override
    public PostResponse getPostDetail(Long id, Long currentUserId) {
        ForumPost post = getVisiblePost(id);
        postRepository.incrementViewCount(id);
        post.setViewCount(post.getViewCount() + 1);
        return toPostResponse(post, currentUserId);
    }

    @Override
    public PageResponse<PostListItem> getPostList(Integer pageNum, Integer pageSize, Long topicId,
                                                   String keyword, String sortBy, Long userId, String status, Long currentUserId) {
        Sort sort = resolveSort(sortBy);
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, sort);
        String queryStatus = status != null && !status.isBlank() ? status : STATUS_PUBLISHED;
        Page<ForumPost> postPage = postRepository.findPosts(topicId, userId, queryStatus, keyword, pageRequest);
        List<PostListItem> items = postPage.getContent().stream()
                .map(post -> toPostListItem(post, currentUserId))
                .collect(Collectors.toList());
        return new PageResponse<>(items, postPage.getTotalElements(), safePage, safeSize);
    }

    @Override
    public PageResponse<HotPostItem> getHotPosts(Integer pageNum, Integer pageSize) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Page<ForumPost> postPage = postRepository.findHotPosts(PageRequest.of(safePage - 1, safeSize));
        List<HotPostItem> items = postPage.getContent().stream()
                .map(this::toHotPostItem)
                .collect(Collectors.toList());
        return new PageResponse<>(items, postPage.getTotalElements(), safePage, safeSize);
    }

    @Override
    public PageResponse<UserPostResponse> getUserPost(Long userId, Integer pageNum, Integer pageSize) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Page<ForumPost> postPage = postRepository.findByUserIdAndStatus(
                userId, STATUS_PUBLISHED, PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime")));
        List<UserPostResponse> items = postPage.getContent().stream()
                .map(post -> {
                    UserPostResponse response = new UserPostResponse();
                    response.setId(post.getId());
                    response.setTitle(post.getTitle());
                    response.setContent(post.getContent());
                    response.setImages(post.getImages());
                    response.setViewCount(post.getViewCount());
                    response.setLikeCount(post.getLikeCount());
                    response.setCommentCount(post.getCommentCount());
                    response.setCreateTime(post.getCreateTime());
                    return response;
                })
                .collect(Collectors.toList());
        return new PageResponse<>(items, postPage.getTotalElements(), safePage, safeSize);
    }

    @Override
    public PageResponse<UserLikeResponse> getUserLikes(Long userId, Integer pageNum, Integer pageSize) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Page<ForumLike> likePage = likeRepository.findByUserIdAndTargetType(
                userId, ForumLike.TARGET_TYPE_POST,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime")));
        List<UserLikeResponse> items = likePage.getContent().stream()
                .map(like -> {
                    UserLikeResponse response = new UserLikeResponse();
                    response.setId(like.getId());
                    response.setPostId(like.getTargetId());
                    ForumPost post = postRepository.findById(like.getTargetId()).orElse(null);
                    if (post != null && STATUS_PUBLISHED.equals(post.getStatus())) {
                        response.setPostTitle(post.getTitle());
                        response.setContent(post.getContent());
                        response.setImages(post.getImages());
                        response.setLikeCount(post.getLikeCount());
                        response.setCommentCount(post.getCommentCount());
                    }
                    response.setCreateTime(like.getCreateTime());
                    return response;
                })
                .collect(Collectors.toList());
        return new PageResponse<>(items, likePage.getTotalElements(), safePage, safeSize);
    }

    @Override
    public void batchDeletePostsByAdmin(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> postRepository.findById(id).ifPresent(this::softDeletePost));
    }

    @Override
    public void togglePin(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(400, "已删除的帖子不能置顶");
        }
        int currentPin = post.getPinOrder() != null ? post.getPinOrder() : 0;
        int newPin = currentPin > 0 ? 0 : 999999;
        post.setPinOrder(newPin);
        postRepository.save(post);
    }

    @Override
    public void toggleHighlight(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(400, "已删除的帖子不能加精");
        }
        Boolean current = post.getHighlighted() != null ? post.getHighlighted() : false;
        post.setHighlighted(!current);
        postRepository.save(post);
    }

    @Override
    public void toggleHidden(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(400, "已删除的帖子不能隐藏");
        }
        if (STATUS_HIDDEN.equals(post.getStatus())) {
            post.setStatus(STATUS_PUBLISHED);
        } else {
            post.setStatus(STATUS_HIDDEN);
        }
        postRepository.save(post);
    }

    @Override
    public PageResponse<PostListItem> getAdminPostList(Integer pageNum, Integer pageSize, String keyword, String status, String sortBy, Long topicId) {
        Sort sort = resolveSort(sortBy);
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, sort);
        Page<ForumPost> postPage = postRepository.findAdminPosts(topicId, status, keyword, pageRequest);
        List<PostListItem> items = postPage.getContent().stream()
                .map(post -> toPostListItem(post, null))
                .collect(Collectors.toList());
        return new PageResponse<>(items, postPage.getTotalElements(), safePage, safeSize);
    }

    private ForumPost getVisiblePost(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (!STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, "帖子不存在或已删除");
        }
        return post;
    }

    private void softDeletePost(ForumPost post) {
        if (STATUS_DELETED.equals(post.getStatus())) {
            return;
        }
        post.setStatus(STATUS_DELETED);
        favoriteRepository.deleteByPostId(post.getId());
        likeRepository.deleteByTargetIdAndTargetType(post.getId(), ForumLike.TARGET_TYPE_POST);
        postRepository.save(post);
        if (post.getTopicId() != null) {
            topicRepository.decrementPostCount(post.getTopicId());
        }
    }

    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "图片列表格式错误");
        }
    }

    private void ensureTopicExists(Long topicId) {
        if (topicId == null) {
            return;
        }
        ForumTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(400, "话题不存在，请重新选择话题"));
        if (!"ACTIVE".equals(topic.getStatus())) {
            throw new BusinessException(400, "该话题已停用");
        }
    }

    private Sort resolveSort(String sortBy) {
        if ("likeCount".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "likeCount", "createTime");
        }
        if ("commentCount".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "commentCount", "createTime");
        }
        return Sort.by(Sort.Direction.DESC, "createTime");
    }

    private HotPostItem toHotPostItem(ForumPost post) {
        HotPostItem item = new HotPostItem();
        item.setId(post.getId());
        item.setTitle(post.getTitle());
        item.setViewCount(post.getViewCount());
        item.setLikeCount(post.getLikeCount());
        item.setCommentCount(post.getCommentCount());
        item.setUserId(post.getUserId());
        item.setCreateTime(post.getCreateTime());
        item.setUsername(resolveUserName(post.getUserId()));
        return item;
    }

    private PostResponse toPostResponse(ForumPost post, Long currentUserId) {
        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setImages(post.getImages());
        response.setTopicId(post.getTopicId());
        response.setViewCount(post.getViewCount());
        response.setLikeCount(post.getLikeCount());
        response.setCommentCount(post.getCommentCount());
        response.setStatus(post.getStatus());
        response.setPinOrder(post.getPinOrder());
        response.setHighlighted(post.getHighlighted());
        response.setCreateTime(post.getCreateTime());
        response.setUpdateTime(post.getUpdateTime());
        response.setUsername(resolveUserName(post.getUserId()));
        userRepository.findById(post.getUserId()).ifPresent(user -> response.setAvatar(user.getAvatar()));
        fillTopic(post.getTopicId(), response);
        response.setIsLiked(currentUserId != null
                && likeRepository.existsByUserIdAndTargetIdAndTargetType(
                currentUserId, post.getId(), ForumLike.TARGET_TYPE_POST));
        response.setIsFavorited(currentUserId != null && favoriteRepository.existsByUserIdAndPostId(currentUserId, post.getId()));
        return response;
    }

    @Override
    public PostListItem toPostListItem(ForumPost post, Long currentUserId) {
        PostListItem item = new PostListItem();
        item.setId(post.getId());
        item.setUserId(post.getUserId());
        item.setTitle(post.getTitle());
        item.setContent(post.getContent() != null && post.getContent().length() > 100
                ? post.getContent().substring(0, 100) + "..."
                : post.getContent());
        item.setImages(post.getImages());
        item.setTopicId(post.getTopicId());
        item.setViewCount(post.getViewCount());
        item.setLikeCount(post.getLikeCount());
        item.setCommentCount(post.getCommentCount());
        item.setStatus(post.getStatus());
        item.setPinOrder(post.getPinOrder());
        item.setHighlighted(post.getHighlighted());
        item.setCreateTime(post.getCreateTime());
        item.setUsername(resolveUserName(post.getUserId()));
        userRepository.findById(post.getUserId()).ifPresent(user -> item.setAvatar(user.getAvatar()));
        fillTopic(post.getTopicId(), item);
        item.setIsLiked(currentUserId != null
                && likeRepository.existsByUserIdAndTargetIdAndTargetType(
                currentUserId, post.getId(), ForumLike.TARGET_TYPE_POST));
        item.setIsFavorited(currentUserId != null && favoriteRepository.existsByUserIdAndPostId(currentUserId, post.getId()));
        return item;
    }

    private void fillTopic(Long topicId, PostResponse response) {
        if (topicId != null) {
            topicRepository.findById(topicId).ifPresent(topic -> response.setTopicName(topic.getTopicName()));
        }
    }

    private void fillTopic(Long topicId, PostListItem item) {
        if (topicId != null) {
            topicRepository.findById(topicId).ifPresent(topic -> item.setTopicName(topic.getTopicName()));
        }
    }

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : user.getUsername())
                .orElse("匿名用户");
    }

    @Override
    public long countAllPosts() {
        return postRepository.count();
    }

    @Override
    public long countByStatus(String status) {
        return postRepository.countByStatus(status);
    }
}
