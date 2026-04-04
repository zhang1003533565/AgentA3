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

            // 提取 innerText 部分
            String innerText = extractInnerText(block);
            if (innerText != null && !innerText.isEmpty()) {
                CourseSchedule schedule = parseInnerText(innerText.trim(), userId, studentId);
                if (schedule != null && schedule.getCourseName() != null) {
                    schedules.add(schedule);
                }
            }
        }

        return schedules;
    }

    /**
     * 从块中提取 innerText
     */
    private static String extractInnerText(String block) {
        // 查找 "innerText": "..." 部分
        Pattern pattern = Pattern.compile("\"innerText\"\\s*:\\s*\"([^\"]*(?:\\\\\"[^\"]*)*)\"");
        Matcher matcher = pattern.matcher(block);
        if (matcher.find()) {
            String text = matcher.group(1);
            // 处理转义字符
            text = text.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
            return text;
        }
        return null;
    }

    /**
     * 解析 innerText 内容
     * 格式：课程名称 周数：X-X 周 校区：XX 校区 上课地点：XXX 教师：XXX 教学班：XXX 教学班组成：XXX 考核方式：XXX 选课备注：XXX 课程学时组成：理论:XX, 上机:XX 周学时：X 总学时：X 学分：X.X
     */
    private static CourseSchedule parseInnerText(String innerText, Long userId, String studentId) {
        CourseSchedule schedule = new CourseSchedule();
        schedule.setUserId(userId);
        schedule.setStudentId(studentId);

        // 1. 解析课程名称（第一个字段，到"周数："之前）
        int weekIndex = innerText.indexOf("周数：");
        if (weekIndex > 0) {
            String courseName = innerText.substring(0, weekIndex).trim();
            schedule.setCourseName(courseName);
        } else {
            return null;
        }

        // 2. 解析周数
        Pattern weekPattern = Pattern.compile("周数：([^校]+)");
        Matcher weekMatcher = weekPattern.matcher(innerText);
        if (weekMatcher.find()) {
            schedule.setWeekRange(weekMatcher.group(1).trim());
        }

        // 3. 解析校区
        Pattern campusPattern = Pattern.compile("校区:?\\s*([朝阳东西南北上校区]+校区)");
        Matcher campusMatcher = campusPattern.matcher(innerText);
        if (campusMatcher.find()) {
            schedule.setCampus(campusMatcher.group(1).trim());
        }

        // 4. 解析上课地点
        Pattern locationPattern = Pattern.compile("上课地点：([^教教师]*)");
        Matcher locationMatcher = locationPattern.matcher(innerText);
        if (locationMatcher.find()) {
            schedule.setLocation(locationMatcher.group(1).trim());
        }

        // 5. 解析教师
        Pattern teacherPattern = Pattern.compile("教师\\s*：([^教]*)");
        Matcher teacherMatcher = teacherPattern.matcher(innerText);
        if (teacherMatcher.find()) {
            schedule.setTeacherName(teacherMatcher.group(1).trim());
        }

        // 6. 解析教学班
        Pattern classCodePattern = Pattern.compile("教学班：([^教]*)");
        Matcher classCodeMatcher = classCodePattern.matcher(innerText);
        if (classCodeMatcher.find()) {
            schedule.setClassCode(classCodeMatcher.group(1).trim());
        }

        // 7. 解析教学班组成
        Pattern classCompositionPattern = Pattern.compile("教学班组成：([^考]*)");
        Matcher classCompositionMatcher = classCompositionPattern.matcher(innerText);
        if (classCompositionMatcher.find()) {
            schedule.setClassComposition(classCompositionMatcher.group(1).trim());
        }

        // 8. 解析考核方式
        Pattern assessmentPattern = Pattern.compile("考核方式：([^选]*)");
        Matcher assessmentMatcher = assessmentPattern.matcher(innerText);
        if (assessmentMatcher.find()) {
            schedule.setAssessmentType(assessmentMatcher.group(1).trim());
        }

        // 9. 解析课程学时组成
        Pattern hoursPattern = Pattern.compile("课程学时组成：([^周]*)");
        Matcher hoursMatcher = hoursPattern.matcher(innerText);
        if (hoursMatcher.find()) {
            parseHours(schedule, hoursMatcher.group(1).trim());
        }

        // 10. 解析周学时
        Pattern weeklyHoursPattern = Pattern.compile("周学时：(\\d+)");
        Matcher weeklyHoursMatcher = weeklyHoursPattern.matcher(innerText);
        if (weeklyHoursMatcher.find()) {
            schedule.setWeeklyHours(Integer.parseInt(weeklyHoursMatcher.group(1)));
        }

        // 11. 解析总学时
        Pattern totalHoursPattern = Pattern.compile("总学时：(\\d+)");
        Matcher totalHoursMatcher = totalHoursPattern.matcher(innerText);
        if (totalHoursMatcher.find()) {
            schedule.setTotalHours(Integer.parseInt(totalHoursMatcher.group(1)));
        }

        // 12. 解析学分
        Pattern creditPattern = Pattern.compile("学分：([\\d.]+)");
        Matcher creditMatcher = creditPattern.matcher(innerText);
        if (creditMatcher.find()) {
            try {
                schedule.setCredit(new BigDecimal(creditMatcher.group(1)));
            } catch (NumberFormatException e) {
                // 忽略
            }
        }

        return schedule;
    }

    /**
     * 解析学时信息
     */
    private static void parseHours(CourseSchedule schedule, String hoursInfo) {
        if (hoursInfo == null || hoursInfo.isEmpty()) return;

        // 理论学时
        Pattern theoryPattern = Pattern.compile("理论:(\\d+)");
        Matcher theoryMatcher = theoryPattern.matcher(hoursInfo);
        if (theoryMatcher.find()) {
            schedule.setTheoryHours(Integer.parseInt(theoryMatcher.group(1)));
        }

        // 上机学时
        Pattern labPattern = Pattern.compile("上机:(\\d+)");
        Matcher labMatcher = labPattern.matcher(hoursInfo);
        if (labMatcher.find()) {
            schedule.setLabHours(Integer.parseInt(labMatcher.group(1)));
        }

        // 实验学时
        Pattern expPattern = Pattern.compile("实验:(\\d+)");
        Matcher expMatcher = expPattern.matcher(hoursInfo);
        if (expMatcher.find()) {
            schedule.setLabHours(Integer.parseInt(expMatcher.group(1)));
        }

        // 实践学时
        Pattern practicePattern = Pattern.compile("实践:(\\d+)");
        Matcher practiceMatcher = practicePattern.matcher(hoursInfo);
        if (practiceMatcher.find()) {
            if (schedule.getLabHours() == null || schedule.getLabHours() == 0) {
                schedule.setLabHours(Integer.parseInt(practiceMatcher.group(1)));
            }
        }
    }
}