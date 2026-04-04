package com.example.appbackend.util;

import com.example.appbackend.entity.CourseSchedule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课表数据解析工具类
 */
public class CourseScheduleParser {

    /**
     * 解析课表原始数据
     */
    public static List<CourseSchedule> parse(String rawData, Long userId, String studentId) {
        List<CourseSchedule> schedules = new ArrayList<>();

        if (rawData == null || rawData.trim().isEmpty()) {
            return schedules;
        }

        // 按"=== 课表块"分割
        String[] blocks = rawData.split("=== 课表块 \\d+ ===");

        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i].trim();
            if (block.isEmpty()) continue;

            // 提取各个字段
            String courseName = extractField(block, "courseName");
            String sections = extractField(block, "sections");
            String sectionStartStr = extractField(block, "sectionStart");
            String sectionEndStr = extractField(block, "sectionEnd");
            String weekText = extractField(block, "weekText");
            String weekdayStr = extractField(block, "weekday");
            String location = extractField(block, "location");
            String teacher = extractField(block, "teacher");
            String classCode = extractField(block, "classCode");
            String classComposition = extractField(block, "classComposition");
            String assessmentType = extractField(block, "assessmentType");
            String hourComposition = extractField(block, "hourComposition");
            String weeklyHours = extractField(block, "weeklyHours");
            String totalHours = extractField(block, "totalHours");
            String credit = extractField(block, "credit");

            // 跳过只有课程名称但其他字段都是空的记录（空数据）
            if (courseName == null || courseName.isEmpty()) continue;
            if ((weekText == null || weekText.isEmpty()) &&
                (location == null || location.isEmpty()) &&
                (teacher == null || teacher.isEmpty())) {
                // 跳过空数据
                continue;
            }

            CourseSchedule schedule = new CourseSchedule();
            schedule.setUserId(userId);
            schedule.setStudentId(studentId);
            schedule.setCourseName(courseName);

            // 设置节次信息
            if (sections != null && !sections.isEmpty()) {
                schedule.setClassSessions(sections);
            }
            // 设置周数
            if (weekText != null && !weekText.isEmpty()) {
                schedule.setWeekRange(weekText);
            }
            // 设置星期几
            if (weekdayStr != null && !weekdayStr.isEmpty() && !"null".equals(weekdayStr)) {
                try {
                    schedule.setWeekday(Integer.parseInt(weekdayStr));
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            // 设置地点
            if (location != null && !location.isEmpty()) {
                schedule.setLocation(location);
            }
            // 设置教师
            if (teacher != null && !teacher.isEmpty()) {
                schedule.setTeacherName(teacher);
            }
            // 设置教学班
            if (classCode != null && !classCode.isEmpty()) {
                schedule.setClassCode(classCode);
            }
            // 设置教学班组成
            if (classComposition != null && !classComposition.isEmpty()) {
                schedule.setClassComposition(classComposition);
            }
            // 设置考核方式
            if (assessmentType != null && !assessmentType.isEmpty()) {
                schedule.setAssessmentType(assessmentType);
            }
            // 解析学时组成
            if (hourComposition != null && !hourComposition.isEmpty()) {
                parseHours(schedule, hourComposition);
            }
            // 设置周学时
            if (weeklyHours != null && !weeklyHours.isEmpty()) {
                try {
                    schedule.setWeeklyHours(Integer.parseInt(weeklyHours));
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            // 设置总学时
            if (totalHours != null && !totalHours.isEmpty()) {
                try {
                    schedule.setTotalHours(Integer.parseInt(totalHours));
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            // 设置学分
            if (credit != null && !credit.isEmpty()) {
                try {
                    schedule.setCredit(new BigDecimal(credit));
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }

            schedules.add(schedule);
        }

        return schedules;
    }

    /**
     * 从块中提取字段值
     */
    private static String extractField(String block, String fieldName) {
        // 匹配 "fieldName": "value" 或 "fieldName": number
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"?([^\"\\n]+)\"?");
        Matcher matcher = pattern.matcher(block);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            // 如果是 null 字面量，返回 null
            if ("null".equals(value)) {
                return null;
            }
            return value;
        }
        return null;
    }

    /**
     * 解析学时信息
     * 格式：理论:24, 上机:12 或 理论:24,实验:8
     */
    private static void parseHours(CourseSchedule schedule, String hoursInfo) {
        if (hoursInfo == null || hoursInfo.isEmpty()) return;

        // 解析理论学时
        Pattern theoryPattern = Pattern.compile("理论\\s*:\\s*(\\d+)");
        Matcher theoryMatcher = theoryPattern.matcher(hoursInfo);
        if (theoryMatcher.find()) {
            schedule.setTheoryHours(Integer.parseInt(theoryMatcher.group(1)));
        }

        // 解析上机学时
        Pattern labPattern = Pattern.compile("上机\\s*:\\s*(\\d+)");
        Matcher labMatcher = labPattern.matcher(hoursInfo);
        if (labMatcher.find()) {
            schedule.setLabHours(Integer.parseInt(labMatcher.group(1)));
        }

        // 解析实验学时
        Pattern expPattern = Pattern.compile("实验\\s*:\\s*(\\d+)");
        Matcher expMatcher = expPattern.matcher(hoursInfo);
        if (expMatcher.find()) {
            if (schedule.getLabHours() == null || schedule.getLabHours() == 0) {
                schedule.setLabHours(Integer.parseInt(expMatcher.group(1)));
            }
        }
    }
}