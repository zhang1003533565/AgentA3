package com.example.appbackend.service.impl;

import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.CourseScheduleRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.util.CourseScheduleParser;
import com.example.appbackend.util.WeekCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseScheduleServiceImpl implements CourseScheduleService {

    @Autowired
    private CourseScheduleRepository courseScheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void saveSchedule(Long userId, String studentId, String rawData) {
        // 先删除旧数据
        courseScheduleRepository.deleteByUserId(userId);

        // 使用工具类解析课表数据
        List<CourseSchedule> schedules = CourseScheduleParser.parse(rawData, userId, studentId);

        System.out.println("DEBUG - 解析到的有效课程数：" + schedules.size());

        // 保存新课表
        courseScheduleRepository.saveAll(schedules);
    }

    @Override
    public List<CourseSchedule> getUserSchedule(Long userId) {
        return courseScheduleRepository.findByUserId(userId);
    }

    @Override
    public List<CourseSchedule> getUserScheduleByStudentId(String studentId) {
        return courseScheduleRepository.findByStudentId(studentId);
    }

    @Override
    @Transactional
    public void deleteSchedule(Long userId) {
        courseScheduleRepository.deleteByUserId(userId);
    }

    @Override
    public List<CourseSchedule> getCurrentWeekSchedule(Long userId) {
        // 获取用户信息（包含学期开始日期）
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 计算当前周次
        int currentWeek = WeekCalculator.getCurrentWeek(user.getSemesterStart());

        System.out.println("DEBUG - 当前周次：" + currentWeek + ", 学期开始日期：" + user.getSemesterStart());

        if (currentWeek <= 0) {
            System.out.println("DEBUG - 学期还未开始或没有设置学期开始日期");
            return List.of();
        }

        // 获取用户的所有课表
        List<CourseSchedule> allSchedules = courseScheduleRepository.findByUserId(userId);

        // 过滤出本周的课程
        return allSchedules.stream()
                .filter(schedule -> {
                    String weekRange = schedule.getWeekRange();
                    System.out.println("DEBUG - 课程：" + schedule.getCourseName() + ", 周次范围：" + weekRange + ", 当前周次：" + currentWeek);
                    boolean isInWeek = WeekCalculator.isWeekInRange(weekRange, currentWeek);
                    if (isInWeek) {
                        System.out.println("DEBUG - 课程 " + schedule.getCourseName() + " 本周上课");
                    } else {
                        System.out.println("DEBUG - 课程 " + schedule.getCourseName() + " 本周不上课");
                    }
                    return isInWeek;
                })
                .collect(Collectors.toList());
    }
}