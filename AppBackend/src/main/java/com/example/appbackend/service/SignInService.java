package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.SignInListItem;
import com.example.appbackend.entity.SignIn;

public interface SignInService {

    /**
     * 发布签到（老师）
     */
    void openSignIn(Long activityId);

    /**
     * 结束签到（老师）
     */
    void closeSignIn(Long activityId);

    /**
     * 检查签到是否开启
     */
    boolean isSignInOpen(Long activityId);

    /**
     * 签到
     */
    SignIn signIn(Long activityId, Long userId);

    /**
     * 补签（根据活动ID和学生ID）
     * @param activityId 活动ID
     * @param studentId 学生ID
     * @param teacherId 教师ID
     * @return 补签后的签到记录
     */
    SignIn supplementSignInByActivityAndUser(Long activityId, Long studentId, Long teacherId);

    /**
     * 获取活动的签到列表
     */
    PageResponse<SignInListItem> getActivitySignIns(Long activityId, Integer page, Integer size);

    /**
     * 获取我的签到状态
     */
    SignIn getSignInStatus(Long activityId, Long userId);

    /**
     * 签到后审核并发放学分
     */
    void reviewSignInAndGrantCredit(Long signInId, String reviewStatus, Long reviewerId, String remark);

    /**
     * 批量签到后审核并发放学分
     */
    void batchReviewSignInAndGrantCredit(Long[] signInIds, String reviewStatus, Long reviewerId, String remark);
}
