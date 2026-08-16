package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.LikeRequest;
import com.example.appbackend.dto.LikeStatusResponse;
import com.example.appbackend.entity.AppMessage;
import com.example.appbackend.entity.ForumLike;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumCommentRepository;
import com.example.appbackend.repository.ForumLikeRepository;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.AppMessageService;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppMessageService appMessageService;

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
            // 取消点赞：帖子不再被赞时移除对应通知
            if (ForumLike.TARGET_TYPE_POST.equals(targetType)) {
                ForumPost post = postRepository.findById(targetId).orElse(null);
                if (post != null && !post.getUserId().equals(userId)) {
                    appMessageService.deleteBySource(
                            AppMessage.SOURCE_TYPE_POST, targetId, post.getUserId(), AppMessage.EVENT_POST_LIKE);
                }
            }
        } else {
            ForumLike newLike = new ForumLike();
            newLike.setUserId(userId);
            newLike.setTargetId(targetId);
            newLike.setTargetType(targetType);
            likeRepository.save(newLike);
            incrementTargetLikeCount(targetId, targetType);
            liked = true;
            // 点赞：给帖子作者发送「收到点赞」通知（自己给自己点赞不发）
            if (ForumLike.TARGET_TYPE_POST.equals(targetType)) {
                notifyPostAuthorOfLike(targetId, userId);
            }
        }

        long likeCount = likeRepository.countByTargetIdAndTargetType(targetId, targetType);
        return new LikeStatusResponse(liked, likeCount);
    }

    /** 向帖子作者发送一条「收到点赞」聚合消息（同一帖子多次被赞只保留一条，并重置为未读） */
    private void notifyPostAuthorOfLike(Long postId, Long likerId) {
        ForumPost post = postRepository.findById(postId).orElse(null);
        if (post == null || post.getUserId() == null || post.getUserId().equals(likerId)) {
            return;
        }
        String likerName = userRepository.findById(likerId)
                .map(this::displayName)
                .orElse("匿名用户");
        String postTitle = post.getTitle() != null && !post.getTitle().isBlank() ? post.getTitle() : "你的帖子";
        AppMessageDTO.CreateCommand command = new AppMessageDTO.CreateCommand();
        command.setUserId(post.getUserId());
        command.setModuleType(AppMessage.MODULE_FORUM);
        command.setEventType(AppMessage.EVENT_POST_LIKE);
        command.setTitle(likerName + " 赞了你的帖子");
        command.setContent("「" + postTitle + "」收到了新的点赞");
        command.setTargetPage("/subpackage_forum/postDetail/postDetail");
        command.setTargetParams("{\"id\":" + postId + "}");
        command.setSourceType(AppMessage.SOURCE_TYPE_POST);
        command.setSourceId(postId);
        appMessageService.createOrRefreshUnread(command);
    }

    private String displayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
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
