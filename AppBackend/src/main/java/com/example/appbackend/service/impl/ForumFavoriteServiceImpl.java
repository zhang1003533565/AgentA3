package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FavoriteStatusResponse;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.PostListItem;
import com.example.appbackend.entity.ForumFavorite;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumFavoriteRepository;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.service.ForumFavoriteService;
import com.example.appbackend.service.PostService;
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
public class ForumFavoriteServiceImpl implements ForumFavoriteService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    @Autowired
    private ForumFavoriteRepository favoriteRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private PostService postService;

    @Override
    public FavoriteStatusResponse toggleFavorite(Long postId, Long userId) {
        ForumPost post = getVisiblePost(postId);
        Optional<ForumFavorite> existing = favoriteRepository.findByUserIdAndPostId(userId, post.getId());
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return new FavoriteStatusResponse(false);
        }

        ForumFavorite favorite = new ForumFavorite();
        favorite.setUserId(userId);
        favorite.setPostId(post.getId());
        favoriteRepository.save(favorite);
        return new FavoriteStatusResponse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public FavoriteStatusResponse getFavoriteStatus(Long postId, Long userId) {
        getVisiblePost(postId);
        boolean favorited = userId != null && favoriteRepository.existsByUserIdAndPostId(userId, postId);
        return new FavoriteStatusResponse(favorited);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostListItem> getMyFavorites(Long userId, Integer pageNum, Integer pageSize) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ForumFavorite> favoritePage = favoriteRepository.findByUserIdAndPost_Status(userId, STATUS_PUBLISHED, pageRequest);
        List<PostListItem> records = favoritePage.getContent().stream()
                .map(ForumFavorite::getPost)
                .filter(post -> post != null)
                .map(post -> postService.toPostListItem(post, userId))
                .collect(Collectors.toList());
        return new PageResponse<>(records, favoritePage.getTotalElements(), safePage, safeSize);
    }

    private ForumPost getVisiblePost(Long postId) {
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        if (!STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(404, "帖子不存在或已删除");
        }
        return post;
    }
}
