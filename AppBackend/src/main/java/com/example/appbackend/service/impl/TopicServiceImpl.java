package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.TopicRequest;
import com.example.appbackend.dto.TopicResponse;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.entity.ForumTopic;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.repository.ForumTopicRepository;
import com.example.appbackend.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TopicServiceImpl implements TopicService {

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private ForumPostRepository postRepository;

    /** 热门/最新收录时间窗（天），与 PostServiceImpl 保持一致 */
    private static final int RECENT_TOPIC_DAYS = 7;
    /** 热门收录上限 */
    private static final int HOT_TOPIC_LIMIT = 20;

    @Override
    public TopicResponse createTopic(TopicRequest request) {
        ForumTopic topic = new ForumTopic();
        topic.setTopicName(request.getTopicName());
        topic.setTopicIcon(request.getTopicIcon());
        topic.setDescription(request.getDescription());
        topic.setIsHot(request.getIsHot() != null ? request.getIsHot() : 0);
        topic.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");

        ForumTopic savedTopic = topicRepository.save(topic);
        return toTopicResponse(savedTopic);
    }

    @Override
    public TopicResponse updateTopic(Long id, TopicRequest request) {
        ForumTopic topic = topicRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));

        topic.setTopicName(request.getTopicName());
        topic.setTopicIcon(request.getTopicIcon());
        topic.setDescription(request.getDescription());
        if (request.getIsHot() != null) {
            topic.setIsHot(request.getIsHot());
        }
        if (request.getStatus() != null) {
            topic.setStatus(request.getStatus());
        }

        ForumTopic updatedTopic = topicRepository.save(topic);
        return toTopicResponse(updatedTopic);
    }

    @Override
    public void deleteTopic(Long id) {
        if (!topicRepository.existsById(id)) {
            throw new BusinessException(404, "话题不存在");
        }
        topicRepository.deleteById(id);
    }

    @Override
    public void batchDeleteTopics(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> topicRepository.findById(id).ifPresent(topicRepository::delete));
    }

    @Override
    public TopicResponse getTopicById(Long id) {
        ForumTopic topic = topicRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "话题不存在"));
        return toTopicResponse(topic);
    }

    @Override
    public PageResponse<TopicResponse> getTopicList(Integer pageNum, Integer pageSize, Integer isHot, String status) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize);
        Page<ForumTopic> topicPage;

        if (isHot != null && status != null) {
            topicPage = topicRepository.findByIsHotAndStatus(isHot, status, pageRequest);
        } else if (isHot != null) {
            topicPage = topicRepository.findByIsHotAndStatus(isHot, "ACTIVE", pageRequest);
        } else if (status != null) {
            topicPage = topicRepository.findByStatus(status, pageRequest);
        } else {
            // 管理后台：不传状态时返回全部话题（含已禁用），便于管理员查看与管理
            topicPage = topicRepository.findAll(pageRequest);
        }

        // 热门/最新为动态聚合话题：帖子数按收录标准实时计算（最新=近7天发布数，热门=热度前20）
        List<ForumPost> recent = postRepository.findRecentPublished(LocalDateTime.now().minusDays(RECENT_TOPIC_DAYS));
        int latestCount = recent.size();
        int hotCount = Math.min(latestCount, HOT_TOPIC_LIMIT);

        List<TopicResponse> topics = topicPage.getContent().stream()
                .map(topic -> {
                    TopicResponse resp = toTopicResponse(topic);
                    if (topic.getId() != null && topic.getId() == 1L) {
                        resp.setPostCount(hotCount);
                    } else if (topic.getId() != null && topic.getId() == 2L) {
                        resp.setPostCount(latestCount);
                    }
                    return resp;
                })
                .collect(Collectors.toList());

        return new PageResponse<>(topics, topicPage.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public List<TopicResponse> getHotTopics(Integer limit) {
        int size = (limit != null && limit > 0) ? limit : 5;
        Pageable pageable = PageRequest.of(0, size);
        List<ForumTopic> hotTopics = topicRepository.findByStatusOrderByPostCountDesc("ACTIVE", pageable);
        return hotTopics.stream()
                .map(this::toTopicResponse)
                .collect(Collectors.toList());
    }

    private TopicResponse toTopicResponse(ForumTopic topic) {
        TopicResponse response = new TopicResponse();
        response.setId(topic.getId());
        response.setTopicName(topic.getTopicName());
        response.setTopicIcon(topic.getTopicIcon());
        response.setDescription(topic.getDescription());
        response.setPostCount(topic.getPostCount());
        response.setIsHot(topic.getIsHot());
        response.setStatus(topic.getStatus());
        response.setCreateTime(topic.getCreateTime());
        return response;
    }

    @Override
    public long countAllTopics() {
        return topicRepository.count();
    }

    @Override
    public long countByStatus(String status) {
        return topicRepository.countByStatus(status);
    }

    @Override
    public long countHotTopics() {
        // 只统计可编辑话题中的热门（排除系统内置的「热门」「最新」）
        return topicRepository.countEditableHotTopics();
    }
}
