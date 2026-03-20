package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.TopicRequest;
import com.example.appbackend.dto.TopicResponse;
import com.example.appbackend.entity.ForumTopic;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumTopicRepository;
import com.example.appbackend.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TopicServiceImpl implements TopicService {

    @Autowired
    private ForumTopicRepository topicRepository;

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
            topicPage = topicRepository.findByStatus("ACTIVE", pageRequest);
        }

        List<TopicResponse> topics = topicPage.getContent().stream()
                .map(this::toTopicResponse)
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
}
