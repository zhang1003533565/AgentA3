package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.RegistrationListItem;
import com.example.appbackend.entity.Registration;

public interface RegistrationService {

    /**
     * 报名活动
     */
    Registration registerActivity(Long activityId, Long userId);

    /**
     * 取消报名
     */
    Registration cancelRegistration(Long registrationId, Long userId);

    /**
     * 管理端移除报名
     */
    void removeRegistrationByManager(Long registrationId);

    /**
     * 获取我的报名列表
     */
    PageResponse<Registration> getMyRegistrations(Long userId, Integer page, Integer size);

    /**
     * 获取活动的报名列表
     */
    PageResponse<RegistrationListItem> getActivityRegistrations(Long activityId, Integer page, Integer size);

    /**
     * 审核报名
     */
    void auditRegistration(Long registrationId, String auditStatus, Long auditorId, String remark);

    /**
     * 批量审核报名
     */
    void batchAuditRegistration(Long[] registrationIds, String auditStatus, Long auditorId, String remark);
}
