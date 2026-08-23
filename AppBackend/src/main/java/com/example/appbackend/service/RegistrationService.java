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
     * 管理端手动为学生添加报名（不受报名时间窗口限制，仍校验名额与重复）
     */
    Registration adminRegisterActivity(Long activityId, Long userId);

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
     * 获取全部报名列表（管理端，可按活动/状态/关键词筛选）
     */
    PageResponse<RegistrationListItem> getAllRegistrations(Long activityId, String status, String keyword, Integer page, Integer size);

    /**
     * 审核报名
     */
    void auditRegistration(Long registrationId, String auditStatus, Long auditorId, String remark);

    /**
     * 批量审核报名
     */
    void batchAuditRegistration(Long[] registrationIds, String auditStatus, Long auditorId, String remark);
}
