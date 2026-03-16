package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;

public interface ActivityService {

    /**
     * 获取活动列表
     */
    PageResponse<Activity> getActivityList(Integer page, Integer size, String title, Long categoryId, Status status);

    /**
     * 获取活动详情
     */
    Activity getActivityById(Long id);

    /**
     * 创建活动
     */
    Activity createActivity(Activity activity, Long userId, String organizerName);

    /**
     * 更新活动
     */
    Activity updateActivity(Long id, Activity activity);

    /**
     * 删除活动
     */
    void deleteActivity(Long id);

    /**
     * 更新活动状态
     */
    void updateActivityStatus(Long id, Status status);

    /**
     * 提交审核
     */
    void submitActivity(Long id);

    /**
     * 审核活动
     */
    void auditActivity(Long id, String auditStatus);

    /**
     * 获取热门活动
     */
    PageResponse<Activity> getHotActivities(Integer page, Integer size);
}
