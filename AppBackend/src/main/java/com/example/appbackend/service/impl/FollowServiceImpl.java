package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FollowListItem;
import com.example.appbackend.dto.FollowRequest;
import com.example.appbackend.dto.FollowStatusResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ForumFollow;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumFollowRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class FollowServiceImpl implements FollowService {

    @Autowired
    private ForumFollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean toggleFollow(FollowRequest request, Long userId) {
        Long followId = request.getFollowId();

        if (userId.equals(followId)) {
            throw new BusinessException(400, "不能关注自己");
        }

        if (!userRepository.existsById(followId)) {
            throw new BusinessException(404, "用户不存在");
        }

        Optional<ForumFollow> existing = followRepository.findByUserIdAndFollowId(userId, followId);

        if (existing.isPresent()) {
            followRepository.delete(existing.get());
            return false;
        } else {
            ForumFollow newFollow = new ForumFollow();
            newFollow.setUserId(userId);
            newFollow.setFollowId(followId);
            followRepository.save(newFollow);
            return true;
        }
    }

    @Override
    public PageResponse<FollowListItem> getFollowers(Long userId, Long currentUserId, Integer pageNum, Integer pageSize) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(404, "用户不存在");
        }

        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ForumFollow> page = followRepository.findByFollowId(userId, pageRequest);

        List<FollowListItem> items = page.getContent().stream()
                .map(follow -> {
                    User user = userRepository.findById(follow.getUserId()).orElse(null);
                    boolean isFollowing = currentUserId != null
                            && followRepository.existsByUserIdAndFollowId(currentUserId, follow.getUserId());
                    return buildListItem(follow, user, isFollowing, true);
                })
                .collect(Collectors.toList());

        return new PageResponse<>(items, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public PageResponse<FollowListItem> getFollowing(Long userId, Long currentUserId, Integer pageNum, Integer pageSize) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(404, "用户不存在");
        }

        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ForumFollow> page = followRepository.findByUserId(userId, pageRequest);

        List<FollowListItem> items = page.getContent().stream()
                .map(follow -> {
                    User user = userRepository.findById(follow.getFollowId()).orElse(null);
                    boolean isFollowing = currentUserId != null
                            && followRepository.existsByUserIdAndFollowId(currentUserId, follow.getFollowId());
                    return buildListItem(follow, user, isFollowing, false);
                })
                .collect(Collectors.toList());

        return new PageResponse<>(items, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public FollowStatusResponse getFollowStatus(Long targetUserId, Long currentUserId) {
        if (!userRepository.existsById(targetUserId)) {
            throw new BusinessException(404, "用户不存在");
        }

        boolean following = currentUserId != null
                && followRepository.existsByUserIdAndFollowId(currentUserId, targetUserId);
        long followerCount = followRepository.countByFollowId(targetUserId);
        long followingCount = followRepository.countByUserId(targetUserId);

        return new FollowStatusResponse(following, followerCount, followingCount);
    }

    private FollowListItem buildListItem(ForumFollow follow, User user, boolean isFollowing, boolean isFollowerList) {
        FollowListItem item = new FollowListItem();
        item.setId(follow.getId());
        item.setIsFollowing(isFollowing);
        item.setCreateTime(follow.getCreateTime());
        if (isFollowerList) {
            item.setUserId(follow.getUserId());
        } else {
            item.setUserId(follow.getFollowId());
            item.setFollowId(follow.getFollowId());
        }
        if (user != null) {
            item.setUsername(user.getUsername());
            item.setAvatar(user.getAvatar());
        }
        return item;
    }
}
