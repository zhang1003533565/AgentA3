package com.example.appbackend.service.impl;

import com.example.appbackend.dto.CommentRequest;
import com.example.appbackend.dto.CommentResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ForumComment;
import com.example.appbackend.entity.ForumLike;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumCommentRepository;
import com.example.appbackend.repository.ForumLikeRepository;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommentServiceImpl implements CommentService {

    private static final String POST_STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_DELETED = "DELETED";

    @Autowired
    private ForumCommentRepository commentRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumLikeRepository likeRepository;

    @Override
    public CommentResponse createComment(CommentRequest request, Long userId) {
        ForumPost post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (!POST_STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, "帖子不存在或已删除");
        }

        if (request.getParentId() != null) {
            ForumComment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new BusinessException(404, "父评论不存在"));
            if (!parent.getPostId().equals(request.getPostId()) || !STATUS_NORMAL.equals(parent.getStatus())) {
                throw new BusinessException(400, "父评论不可回复");
            }
        }

        ForumComment comment = new ForumComment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setReplyToId(request.getReplyToId());
        comment.setContent(request.getContent());
        comment.setLikeCount(0);
        comment.setStatus(STATUS_NORMAL);

        ForumComment saved = commentRepository.save(comment);
        postRepository.incrementCommentCount(request.getPostId());
        return toCommentResponse(saved, new ArrayList<>(), userId);
    }

    @Override
    public void deleteComment(Long id, Long userId) {
        ForumComment comment = getVisibleComment(id);
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此评论");
        }
        softDeleteComment(comment);
    }

    @Override
    public void deleteCommentByAdmin(Long id) {
        ForumComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        softDeleteComment(comment);
    }

    @Override
    public void batchDeleteComments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> commentRepository.findById(id).ifPresent(this::softDeleteComment));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getCommentList(Long postId, Integer pageNum, Integer pageSize, Long currentUserId) {
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (!POST_STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, "帖子不存在或已删除");
        }

        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        Page<ForumComment> page = commentRepository.findByPostIdAndParentIdIsNullAndStatus(
                postId, STATUS_NORMAL, PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime")));
        List<ForumComment> parentComments = page.getContent();
        Map<Long, List<ForumComment>> childrenMap = getChildrenMap(parentComments, STATUS_NORMAL);
        List<CommentResponse> items = parentComments.stream()
                .map(comment -> toCommentResponse(
                        comment, childrenMap.getOrDefault(comment.getId(), new ArrayList<>()), currentUserId))
                .collect(Collectors.toList());
        return new PageResponse<>(items, page.getTotalElements(), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getAdminCommentList(Long postId, String keyword, String status, Integer pageNum, Integer pageSize) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Page<ForumComment> page = commentRepository.findComments(
                postId,
                status != null && !status.isBlank() ? status : null,
                keyword,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime")));
        List<CommentResponse> items = page.getContent().stream()
                .map(comment -> toCommentResponse(comment, new ArrayList<>(), null))
                .collect(Collectors.toList());
        return new PageResponse<>(items, page.getTotalElements(), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentResponse getCommentDetail(Long id, Long currentUserId) {
        ForumComment comment = getVisibleComment(id);
        List<ForumComment> children = commentRepository.findByParentIdAndStatus(id, STATUS_NORMAL);
        return toCommentResponse(comment, children, currentUserId);
    }

    private ForumComment getVisibleComment(Long id) {
        ForumComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        if (!STATUS_NORMAL.equals(comment.getStatus())) {
            throw new BusinessException(404, "评论不存在或已删除");
        }
        return comment;
    }

    private void softDeleteComment(ForumComment comment) {
        if (STATUS_DELETED.equals(comment.getStatus())) {
            return;
        }
        comment.setStatus(STATUS_DELETED);
        commentRepository.save(comment);
        likeRepository.deleteByTargetIdAndTargetType(comment.getId(), ForumLike.TARGET_TYPE_COMMENT);
        postRepository.decrementCommentCount(comment.getPostId());
        List<ForumComment> children = commentRepository.findByParentIdAndStatus(comment.getId(), STATUS_NORMAL);
        children.forEach(this::softDeleteComment);
    }

    private Map<Long, List<ForumComment>> getChildrenMap(List<ForumComment> parentComments, String status) {
        if (parentComments.isEmpty()) {
            return Map.of();
        }
        List<ForumComment> allChildren = new ArrayList<>();
        for (ForumComment parent : parentComments) {
            allChildren.addAll(commentRepository.findByParentIdAndStatus(parent.getId(), status));
        }
        return allChildren.stream().collect(Collectors.groupingBy(ForumComment::getParentId));
    }

    private CommentResponse toCommentResponse(ForumComment comment, List<ForumComment> children, Long currentUserId) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setPostId(comment.getPostId());
        response.setUserId(comment.getUserId());
        response.setParentId(comment.getParentId());
        response.setReplyToId(comment.getReplyToId());
        response.setContent(comment.getContent());
        response.setLikeCount(comment.getLikeCount());
        response.setStatus(comment.getStatus());
        response.setCreateTime(comment.getCreateTime());
        response.setUsername(resolveUserName(comment.getUserId()));
        userRepository.findById(comment.getUserId()).ifPresent(user -> response.setAvatar(user.getAvatar()));
        if (comment.getReplyToId() != null) {
            response.setReplyToUsername(resolveUserName(comment.getReplyToId()));
        }
        // Resolve post title for admin list
        if (comment.getPostId() != null) {
            postRepository.findById(comment.getPostId()).ifPresent(p -> response.setPostTitle(p.getTitle()));
        }
        response.setIsLiked(currentUserId != null
                && likeRepository.existsByUserIdAndTargetIdAndTargetType(
                currentUserId, comment.getId(), ForumLike.TARGET_TYPE_COMMENT));
        response.setChildren(children == null ? new ArrayList<>() : children.stream()
                .map(child -> toCommentResponse(child, new ArrayList<>(), currentUserId))
                .collect(Collectors.toList()));
        return response;
    }

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(this::displayName)
                .orElse("匿名用户");
    }

    private String displayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }
}
