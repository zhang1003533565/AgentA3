package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;

import java.util.List;

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
    Activity draftActivity(Activity activity, Long userId, String organizerName);

    /**
     * 更新活动
     */
    Activity updateActivity(Long id, Activity activity);

    /**
     * 删除活动
     */
    void deleteActivity(Long id);

    /**
     * 批量删除活动
     */
    void deleteActivities(List<Long> ids);

    /**
     * 更新活动状态
     */
    void updateActivityStatus(Long id, Status status);

    /**
     * 提交审核
     */


    /**
     * 审核活动
     */


    /**
     * 模糊搜索活动
     */
    PageResponse<Activity> searchActivities(Integer page, Integer size, String keyword);

    /**
     * 按分类和状态筛选活动
     */
    PageResponse<Activity> filterActivities(Integer page, Integer size, Long categoryId, Status status);


    void publishActivity(Long id);
}
