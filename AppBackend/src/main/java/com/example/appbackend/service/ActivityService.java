package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;

import java.util.List;

public interface ActivityService {

    /**
     * 获取活动列表
     */
    PageResponse<Activity> getActivityList(Integer page, Integer size, String title, Long categoryId, Status status, String timePhase);

    /**
     * 获取我发起的活动（按组织者分页）
     */
    PageResponse<Activity> getActivitiesByOrganizer(Long organizerId, Integer page, Integer size);

    /**
     * 获取活动详情
     */
    Activity getActivityById(Long id);

    /**
     * 创建活动
     */
    Activity draftActivity(Activity activity, Long userId, String organizerName);

    /**
     * 更新活动
     */
    Activity updateActivity(Long id, Activity activity);


    void deleteActivity(Long id, boolean isAdmin);


    void deleteActivities(List<Long> ids, boolean isAdmin);

    /**
     * 更新活动状态
     */
    void updateActivityStatus(Long id, Status status);

    PageResponse<Activity> searchActivities(Integer page, Integer size, String keyword);

    /**
     * 按分类和状态筛选活动
     */
    PageResponse<Activity> filterActivities(Integer page, Integer size, Long categoryId, Status status);


    void publishActivity(Long id);

    /**
     * 更新过期活动状态
     */
    void updateExpiredActivitiesStatus();
}
