package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LikeRequest;
import com.example.appbackend.dto.LikeStatusResponse;
import com.example.appbackend.entity.ForumLike;
import com.example.appbackend.exception.BusinessException;
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

    @Autowired
    private ForumLikeRepository likeRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Override
    public LikeStatusResponse toggleLike(LikeRequest request, Long userId) {
        Long targetId = request.getTargetId();

        if (!postRepository.existsById(targetId)) {
            throw new BusinessException(404, "帖子不存在");
        }

        Optional<ForumLike> existingLike = likeRepository.findByUserIdAndTargetId(userId, targetId);

        boolean liked;
        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            postRepository.decrementLikeCount(targetId);
            liked = false;
        } else {
            ForumLike newLike = new ForumLike();
            newLike.setUserId(userId);
            newLike.setTargetId(targetId);
            likeRepository.save(newLike);
            postRepository.incrementLikeCount(targetId);
            liked = true;
        }

        long likeCount = likeRepository.countByTargetId(targetId);
        return new LikeStatusResponse(liked, likeCount);
    }

    @Override
    public LikeStatusResponse getLikeStatus(Long targetId, Long currentUserId) {
        if (!postRepository.existsById(targetId)) {
            throw new BusinessException(404, "帖子不存在");
        }

        boolean liked = currentUserId != null
                && likeRepository.existsByUserIdAndTargetId(currentUserId, targetId);

        long likeCount = likeRepository.countByTargetId(targetId);
        return new LikeStatusResponse(liked, likeCount);
    }
}
