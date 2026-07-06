package com.example.appbackend.service;

import com.example.appbackend.dto.ScheduleChangeSummary;
import com.example.appbackend.entity.CourseSchedule;
import java.util.List;

public interface CourseScheduleService {

    /**
     * 保存课表数据（先删除旧的，再保存新的）
     */
    void saveSchedule(Long userId, String studentId, String rawData);

    /**
     * 保存指定学期课表数据（只覆盖该学期，不影响其他学期）
     */
    void saveSchedule(Long userId, String studentId, String rawData, String academicYear, Integer semesterTerm, String semesterCode);

    /**
     * 保存指定学期课表数据，并返回与原课表的变更摘要。
     */
    ScheduleChangeSummary saveScheduleAndSummarizeChanges(
            Long userId,
            String studentId,
            String rawData,
            String academicYear,
            Integer semesterTerm,
            String semesterCode
    );

    /**
     * 获取用户的课表
     */
    List<CourseSchedule> getUserSchedule(Long userId);

    /**
     * 获取用户指定学期的课表
     */
    List<CourseSchedule> getUserSchedule(Long userId, String academicYear, Integer semesterTerm);

    /**
     * 获取用户的课表（按学号）
     */
    List<CourseSchedule> getUserScheduleByStudentId(String studentId);

    /**
     * 删除用户的课表
     */
    void deleteSchedule(Long userId);

    /**
     * 删除用户指定学期课表。
     */
    void deleteSchedule(Long userId, String academicYear, Integer semesterTerm);

    /**
     * 获取用户本周的课表
     * @param userId 用户 ID
     * @return 本周课表列表
     */
    List<CourseSchedule> getCurrentWeekSchedule(Long userId);

    /**
     * 获取指定学期开学日期计算出的当前周课表
     */
    List<CourseSchedule> getCurrentWeekSchedule(Long userId, java.time.LocalDate semesterStart, String academicYear, Integer semesterTerm);

    /**
     * 获取用户指定周次的课表
     * @param userId 用户 ID
     * @param week 周次（1-20）
     * @return 指定周次课表列表
     */
    List<CourseSchedule> getWeekSchedule(Long userId, int week);

    /**
     * 获取用户指定学期、指定周次课表
     */
    List<CourseSchedule> getWeekSchedule(Long userId, int week, String academicYear, Integer semesterTerm);

    /**
     * 获取课程详情
     * @param userId 用户 ID
     * @param courseId 课程 ID
     * @return 课程详情
     */
    CourseSchedule getCourseDetail(Long userId, Long courseId);

    /**
     * 通过分享码复制他人课表
     * @param userId 当前用户 ID
     * @param shareCode 课表分享码
     */
    void copyScheduleByShareCode(Long userId, String shareCode);

    /**
     * 通过分享码复制他人指定学期课表
     */
    void copyScheduleByShareCode(Long userId, String shareCode, String academicYear, Integer semesterTerm);
}
