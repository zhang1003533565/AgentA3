package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.AppMessage;
import com.example.appbackend.entity.ForumComment;
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
import com.example.appbackend.repository.AppMessageRepository;
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

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PostServiceImpl implements PostService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_HIDDEN = "HIDDEN";

    /** 最新话题：最近 N 天内发布的帖子 */
    private static final int RECENT_TOPIC_DAYS = 7;
    /** 热门话题：综合热度 TOP N */
    private static final int HOT_TOPIC_LIMIT = 20;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumLikeRepository likeRepository;

    @Autowired
    private AppMessageRepository appMessageRepository;

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
    public ForumMessageUnreadResponse getMessageUnreadCount(Long userId) {
        ForumMessageUnreadResponse response = new ForumMessageUnreadResponse(0L, 0L, 0L);
        if (userId == null) {
            return response;
        }
        // 我的帖子
        List<ForumPost> myPosts = postRepository.findByUserIdAndStatus(userId, STATUS_PUBLISHED, PageRequest.of(0, 1000)).getContent();
        if (myPosts.isEmpty()) {
            return response;
        }
        // 收到的评论数：他人（非本人）评论了我的帖子（顶级评论 + 子评论）
        List<Long> postIds = myPosts.stream().map(ForumPost::getId).collect(Collectors.toList());
        long commentCount = commentRepository.countByPostIdInAndUserIdNot(postIds, userId);
        response.setCommentCount(commentCount);
        // 被点赞的未读消息数（基于真实消息记录，已读后清零；新点赞会重新产生未读）
        long likeCount = appMessageRepository.countByUserIdAndModuleTypeAndEventTypeAndIsReadFalse(
                userId, AppMessage.MODULE_FORUM, AppMessage.EVENT_POST_LIKE);
        response.setLikeCount(likeCount);
        // 系统通知数：公告话题（topicId=3）下的帖子数
        long systemCount = topicRepository.countById(3L);
        response.setSystemCount(systemCount);
        return response;
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

    @Override
    public PageResponse<PostListItem> getRecommendedPosts(String type, Integer pageNum, Integer pageSize) {
        // 最新标准：最近 RECENT_TOPIC_DAYS 天内发布的已发布帖子，按发布时间倒序
        LocalDateTime since = LocalDateTime.now().minusDays(RECENT_TOPIC_DAYS);
        List<ForumPost> recent = postRepository.findRecentPublished(since);

        List<ForumPost> ordered;
        if ("hot".equalsIgnoreCase(type)) {
            // 热门标准：最新时间窗内按综合热度(点赞×3 + 评论×5 + 浏览×0.1)降序，取前 HOT_TOPIC_LIMIT 条
            ordered = recent.stream()
                    .sorted(Comparator.comparingDouble(this::heatScore).reversed())
                    .limit(HOT_TOPIC_LIMIT)
                    .collect(Collectors.toList());
        } else {
            // latest：findRecentPublished 已按 createTime 倒序
            ordered = recent;
        }

        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int from = (safePage - 1) * safeSize;
        List<PostListItem> items = ordered.stream()
                .skip(Math.max(from, 0)).limit(safeSize)
                .map(post -> toPostListItem(post, null))
                .collect(Collectors.toList());
        return new PageResponse<>(items, (long) ordered.size(), safePage, safeSize);
    }

    /** 综合热度：点赞权重最高，评论次之，浏览最低 */
    private double heatScore(ForumPost post) {
        int like = post.getLikeCount() != null ? post.getLikeCount() : 0;
        int comment = post.getCommentCount() != null ? post.getCommentCount() : 0;
        int view = post.getViewCount() != null ? post.getViewCount() : 0;
        return like * 3.0 + comment * 5.0 + view * 0.1;
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
        // 级联删除该帖子的所有评论及其点赞，确保删除后不留任何关联数据
        List<ForumComment> comments = commentRepository.findByPostId(post.getId());
        List<Long> commentIds = comments.stream().map(ForumComment::getId).collect(Collectors.toList());
        if (!commentIds.isEmpty()) {
            likeRepository.deleteByTargetIdsAndTargetType(commentIds, ForumLike.TARGET_TYPE_COMMENT);
        }
        // 先删子评论再删父评论，避免 parent_id 外键约束冲突
        commentRepository.deleteByPostIdChildren(post.getId());
        commentRepository.deleteByPostId(post.getId());
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
            return Sort.by(Sort.Direction.DESC, "pinOrder", "likeCount", "createTime");
        }
        if ("commentCount".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "pinOrder", "commentCount", "createTime");
        }
        return Sort.by(Sort.Direction.DESC, "pinOrder", "createTime");
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
