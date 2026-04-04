package com.example.appbackend.service;

import com.example.appbackend.entity.CourseSchedule;
import java.util.List;

public interface CourseScheduleService {

    /**
     * 保存课表数据（先删除旧的，再保存新的）
     */
    void saveSchedule(Long userId, String studentId, String rawData);

    /**
     * 获取用户的课表
     */
    List<CourseSchedule> getUserSchedule(Long userId);

    /**
     * 获取用户的课表（按学号）
     */
    List<CourseSchedule> getUserScheduleByStudentId(String studentId);

    /**
     * 删除用户的课表
     */
    void deleteSchedule(Long userId);

    /**
     * 获取用户本周的课表
     * @param userId 用户 ID
     * @return 本周课表列表
     */
    List<CourseSchedule> getCurrentWeekSchedule(Long userId);

    /**
     * 获取用户指定周次的课表
     * @param userId 用户 ID
     * @param week 周次（1-20）
     * @return 指定周次课表列表
     */
    List<CourseSchedule> getWeekSchedule(Long userId, int week);

    /**
     * 获取课程详情
     * @param userId 用户 ID
     * @param courseId 课程 ID
     * @return 课程详情
     */
    CourseSchedule getCourseDetail(Long userId, Long courseId);
}