package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LikeRequest;
import com.example.appbackend.dto.LikeStatusResponse;
import com.example.appbackend.entity.ForumLike;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumCommentRepository;
import com.example.appbackend.repository.ForumLikeRepository;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.service.LikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class LikeServiceImpl implements LikeService {

    private static final String POST_STATUS_PUBLISHED = "PUBLISHED";
    private static final String COMMENT_STATUS_NORMAL = "NORMAL";

    @Autowired
    private ForumLikeRepository likeRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private ForumCommentRepository commentRepository;

    @Override
    public LikeStatusResponse toggleLike(LikeRequest request, Long userId) {
        Long targetId = request.getTargetId();
        String targetType = normalizeTargetType(request.getTargetType());

        ensureTargetVisible(targetId, targetType);

        Optional<ForumLike> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType);

        boolean liked;
        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            decrementTargetLikeCount(targetId, targetType);
            liked = false;
        } else {
            ForumLike newLike = new ForumLike();
            newLike.setUserId(userId);
            newLike.setTargetId(targetId);
            newLike.setTargetType(targetType);
            likeRepository.save(newLike);
            incrementTargetLikeCount(targetId, targetType);
            liked = true;
        }

        long likeCount = likeRepository.countByTargetIdAndTargetType(targetId, targetType);
        return new LikeStatusResponse(liked, likeCount);
    }

    @Override
    public LikeStatusResponse getLikeStatus(Long targetId, String targetType, Long currentUserId) {
        String normalizedTargetType = normalizeTargetType(targetType);
        ensureTargetVisible(targetId, normalizedTargetType);

        boolean liked = currentUserId != null
                && likeRepository.existsByUserIdAndTargetIdAndTargetType(currentUserId, targetId, normalizedTargetType);

        long likeCount = likeRepository.countByTargetIdAndTargetType(targetId, normalizedTargetType);
        return new LikeStatusResponse(liked, likeCount);
    }

    private String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            throw new BusinessException(400, "点赞目标类型不能为空");
        }
        String normalized = targetType.trim().toUpperCase();
        if (!ForumLike.TARGET_TYPE_POST.equals(normalized) && !ForumLike.TARGET_TYPE_COMMENT.equals(normalized)) {
            throw new BusinessException(400, "不支持的点赞目标类型");
        }
        return normalized;
    }

    private void ensureTargetVisible(Long targetId, String targetType) {
        if (ForumLike.TARGET_TYPE_POST.equals(targetType)) {
            ForumPost post = postRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
            if (!POST_STATUS_PUBLISHED.equals(post.getStatus())) {
                throw new BusinessException(404, "帖子不存在或已删除");
            }
            return;
        }

        commentRepository.findById(targetId)
                .filter(comment -> COMMENT_STATUS_NORMAL.equals(comment.getStatus()))
                .orElseThrow(() -> new BusinessException(404, "评论不存在或已删除"));
    }

    private void incrementTargetLikeCount(Long targetId, String targetType) {
        if (ForumLike.TARGET_TYPE_POST.equals(targetType)) {
            postRepository.incrementLikeCount(targetId);
        } else {
            commentRepository.incrementLikeCount(targetId);
        }
    }

    private void decrementTargetLikeCount(Long targetId, String targetType) {
        if (ForumLike.TARGET_TYPE_POST.equals(targetType)) {
            postRepository.decrementLikeCount(targetId);
        } else {
            commentRepository.decrementLikeCount(targetId);
        }
    }
}
