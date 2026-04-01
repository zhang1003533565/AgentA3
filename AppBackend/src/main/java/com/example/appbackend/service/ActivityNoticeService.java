package com.example.appbackend.service;

import com.example.appbackend.dto.ActivityNoticeRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ActivityNotice;

public interface ActivityNoticeService {

    PageResponse<ActivityNotice> getNoticeList(Long activityId, String title, String status, Integer pageNum, Integer pageSize);

    ActivityNotice getNoticeById(Long id);

    ActivityNotice createNotice(ActivityNoticeRequest request, Long userId, String userName);

    ActivityNotice updateNotice(Long id, ActivityNoticeRequest request);

    void deleteNotice(Long id);

    PageResponse<ActivityNotice> getNoticesByActivityId(Long activityId, Integer pageNum, Integer pageSize);
}
