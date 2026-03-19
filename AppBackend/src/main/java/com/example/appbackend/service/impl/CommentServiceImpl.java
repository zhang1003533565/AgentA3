package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.ForumComment;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
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

    @Autowired
    private ForumCommentRepository commentRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumLikeRepository likeRepository;

    private static final int TARGET_TYPE_COMMENT = 2;

    @Override
    public CommentResponse createComment(CommentRequest request, Long userId) {
        if (!postRepository.findById(request.getPostId()).isPresent()) {
            throw new BusinessException(404, "帖子不存在");
        }

        if (request.getParentId() != null) {
            ForumComment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new BusinessException(404, "父评论不存在"));
            if (!parent.getPostId().equals(request.getPostId())) {
                throw new BusinessException(400, "父评论不属于该帖子");
            }
        }

        ForumComment comment = new ForumComment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setReplyToId(request.getReplyToId());
        comment.setContent(request.getContent());
        comment.setLikeCount(0);

        ForumComment saved = commentRepository.save(comment);
        postRepository.incrementCommentCount(request.getPostId());

        return toCommentResponse(saved, userId, new ArrayList<>());
    }

    @Override
    public void deleteComment(Long id, Long userId) {
        ForumComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此评论");
        }

        deleteCommentAndDescendants(id);
    }

    @Override
    public PageResponse<CommentResponse> getCommentList(Long postId, Integer pageNum, Integer pageSize, Long currentUserId) {
        if (!postRepository.existsById(postId)) {
            throw new BusinessException(404, "帖子不存在");
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "createTime");
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sort);
        Page<ForumComment> page = commentRepository.findByPostIdAndParentIdIsNull(postId, pageRequest);

        List<ForumComment> parentComments = page.getContent();
        Map<Long, List<ForumComment>> childrenMap = getChildrenMap(parentComments);

        List<CommentResponse> items = parentComments.stream()
                .map(comment -> toCommentResponse(comment, currentUserId, childrenMap.getOrDefault(comment.getId(), new ArrayList<>())))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public CommentResponse getCommentDetail(Long id, Long currentUserId) {
        ForumComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));

        List<ForumComment> children = commentRepository.findByParentId(id);
        return toCommentResponse(comment, currentUserId, children);
    }

    private Map<Long, List<ForumComment>> getChildrenMap(List<ForumComment> parentComments) {
        if (parentComments.isEmpty()) {
            return Map.of();
        }

        List<Long> parentIds = parentComments.stream()
                .map(ForumComment::getId)
                .collect(Collectors.toList());

        List<ForumComment> allChildren = new ArrayList<>();
        for (Long parentId : parentIds) {
            allChildren.addAll(commentRepository.findByParentId(parentId));
        }

        return allChildren.stream()
                .collect(Collectors.groupingBy(ForumComment::getParentId));
    }

    private CommentResponse toCommentResponse(ForumComment comment, Long currentUserId, List<ForumComment> children) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setPostId(comment.getPostId());
        response.setUserId(comment.getUserId());
        response.setParentId(comment.getParentId());
        response.setReplyToId(comment.getReplyToId());
        response.setContent(comment.getContent());
        response.setLikeCount(comment.getLikeCount());
        response.setCreateTime(comment.getCreateTime());

        User user = userRepository.findById(comment.getUserId()).orElse(null);
        response.setUsername(user != null ? user.getRealName() : "匿名用户");
        response.setAvatar(user != null ? user.getAvatar() : null);

        if (comment.getReplyToId() != null) {
            User replyToUser = userRepository.findById(comment.getReplyToId()).orElse(null);
            response.setReplyToUsername(replyToUser != null ? replyToUser.getRealName() : null);
        }

        if (currentUserId != null) {
            response.setIsLiked(likeRepository.existsByUserIdAndTargetIdAndTargetType(
                    currentUserId, comment.getId(), TARGET_TYPE_COMMENT));
        } else {
            response.setIsLiked(false);
        }

        if (children != null && !children.isEmpty()) {
            List<CommentResponse> childResponses = children.stream()
                    .map(child -> toCommentResponse(child, currentUserId, new ArrayList<>()))
                    .collect(Collectors.toList());
            response.setChildren(childResponses);
        } else {
            response.setChildren(new ArrayList<>());
        }

        return response;
    }

    @Override
    public void deleteCommentByAdmin(Long id) {
        commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        deleteCommentAndDescendants(id);
    }

    @Override
    public void batchDeleteComments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            commentRepository.findById(id).ifPresent(c -> deleteCommentAndDescendants(id));
        }
    }

    /**
     * 递归删除评论及其所有子回复（先删子评论再删自身，满足 parent_id 外键约束）。
     */
    private void deleteCommentAndDescendants(Long commentId) {
        ForumComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            return;
        }
        List<ForumComment> children = commentRepository.findByParentId(commentId);
        for (ForumComment child : children) {
            deleteCommentAndDescendants(child.getId());
        }
        postRepository.decrementCommentCount(comment.getPostId());
        commentRepository.deleteById(commentId);
    }
}
