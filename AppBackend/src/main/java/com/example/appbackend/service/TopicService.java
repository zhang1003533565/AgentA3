package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.TopicRequest;
import com.example.appbackend.dto.TopicResponse;

import java.util.List;

public interface TopicService {

    TopicResponse createTopic(TopicRequest request);

    TopicResponse updateTopic(Long id, TopicRequest request);

    void deleteTopic(Long id);

    TopicResponse getTopicById(Long id);

    PageResponse<TopicResponse> getTopicList(Integer pageNum, Integer pageSize, Integer isHot, String status);

    List<TopicResponse> getHotTopics(Integer limit);
}
