package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.entity.ForumTopic;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class PostServiceImpl implements PostService {

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumLikeRepository likeRepository;

    @Autowired
    private ForumCommentRepository commentRepository;

    @Override
    public PostResponse createPost(PostRequest request, Long userId) {
        ForumPost post = new ForumPost();
        post.setUserId(userId);
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setTopicId(request.getTopicId());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);

        ForumPost savedPost = postRepository.save(post);

        if (savedPost.getTopicId() != null) {
            topicRepository.incrementPostCount(savedPost.getTopicId());
        }

        return toPostResponse(savedPost, userId);
    }

    @Override
    public PostResponse updatePost(Long id, PostRequest request, Long userId) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权编辑此帖子");
        }

        Long oldTopicId = post.getTopicId();

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
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
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此帖子");
        }

        if (post.getTopicId() != null) {
            topicRepository.decrementPostCount(post.getTopicId());
        }

        deleteCommentsLeafFirst(id);

        postRepository.deleteById(id);
    }

    @Override
    public void deletePostByAdmin(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));

        if (post.getTopicId() != null) {
            topicRepository.decrementPostCount(post.getTopicId());
        }

        deleteCommentsLeafFirst(id);

        postRepository.deleteById(id);
    }

    @Override
    public void batchDeletePosts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        for (Long id : ids) {
            postRepository.findById(id).ifPresent(post -> {
                if (post.getTopicId() != null) {
                    topicRepository.decrementPostCount(post.getTopicId());
                }
                deleteCommentsLeafFirst(id);
            });
        }

        postRepository.deleteByIds(ids);
    }

    private void deleteCommentsLeafFirst(Long postId) {
        Set<Long> deleted = new HashSet<>();
        List<Long> leafIds = commentRepository.findLeafCommentIdsByPostId(postId);

        while (!leafIds.isEmpty()) {
            deleted.addAll(leafIds);
            commentRepository.deleteByIds(new ArrayList<>(leafIds));

            List<Long> nextBatch = commentRepository.findLeafCommentIdsByPostId(postId)
                    .stream()
                    .filter(id -> !deleted.contains(id))
                    .collect(Collectors.toList());
            leafIds = nextBatch;
        }
    }

    @Override
    public PostResponse getPostDetail(Long id, Long currentUserId) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));

        postRepository.incrementViewCount(id);
        post.setViewCount(post.getViewCount() + 1);

        return toPostResponse(post, currentUserId);
    }

    @Override
    public PageResponse<PostListItem> getPostList(Integer pageNum, Integer pageSize, Long topicId,
                                                   String keyword, String sortBy, Long userId, Long currentUserId) {
        Sort sort;
        if ("likeCount".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "likeCount", "createTime");
        } else if ("commentCount".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "commentCount", "createTime");
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createTime");
        }

        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sort);
        Page<ForumPost> postPage = postRepository.findPosts(topicId, userId, keyword, pageRequest);

        List<PostListItem> items = postPage.getContent().stream()
                .map(post -> toPostListItem(post, currentUserId))
                .collect(Collectors.toList());

        return new PageResponse<>(items, postPage.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public PageResponse<HotPostItem> getHotPosts(Integer pageNum, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize);
        Page<ForumPost> postPage = postRepository.findHotPosts(pageRequest);

        List<HotPostItem> items = postPage.getContent().stream()
                .map(post -> {
                    HotPostItem item = new HotPostItem();
                    item.setId(post.getId());
                    item.setTitle(post.getTitle());
                    item.setViewCount(post.getViewCount());
                    item.setLikeCount(post.getLikeCount());
                    item.setCommentCount(post.getCommentCount());
                    item.setUserId(post.getUserId());
                    item.setCreateTime(post.getCreateTime());

                    User user = userRepository.findById(post.getUserId()).orElse(null);
                    item.setUsername(user != null ? user.getRealName() : "匿名用户");
                    return item;
                })
                .collect(Collectors.toList());

        return new PageResponse<>(items, postPage.getTotalElements(), pageNum, pageSize);
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
        response.setCreateTime(post.getCreateTime());
        response.setUpdateTime(post.getUpdateTime());

        User user = userRepository.findById(post.getUserId()).orElse(null);
        response.setUsername(user != null ? user.getRealName() : "匿名用户");
        response.setAvatar(user != null ? user.getAvatar() : null);

        if (post.getTopicId() != null) {
            ForumTopic topic = topicRepository.findById(post.getTopicId()).orElse(null);
            response.setTopicName(topic != null ? topic.getTopicName() : null);
        }

        if (currentUserId != null) {
            response.setIsLiked(likeRepository.existsByUserIdAndTargetId(
                    currentUserId, post.getId()));
        } else {
            response.setIsLiked(false);
        }

        return response;
    }

    private PostListItem toPostListItem(ForumPost post, Long currentUserId) {
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
        item.setCreateTime(post.getCreateTime());

        User user = userRepository.findById(post.getUserId()).orElse(null);
        item.setUsername(user != null ? user.getRealName() : "匿名用户");
        item.setAvatar(user != null ? user.getAvatar() : null);

        if (post.getTopicId() != null) {
            ForumTopic topic = topicRepository.findById(post.getTopicId()).orElse(null);
            item.setTopicName(topic != null ? topic.getTopicName() : null);
        }

        if (currentUserId != null) {
            item.setIsLiked(likeRepository.existsByUserIdAndTargetId(
                    currentUserId, post.getId()));
        } else {
            item.setIsLiked(false);
        }

        return item;
    }
}
