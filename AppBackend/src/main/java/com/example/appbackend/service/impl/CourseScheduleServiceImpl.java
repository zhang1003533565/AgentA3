package com.example.appbackend.service.impl;

import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.repository.CourseScheduleRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.util.CourseScheduleParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseScheduleServiceImpl implements CourseScheduleService {

    @Autowired
    private CourseScheduleRepository courseScheduleRepository;

    @Override
    @Transactional
    public void saveSchedule(Long userId, String studentId, String rawData) {
        // 先删除旧数据
        courseScheduleRepository.deleteByUserId(userId);

        // 使用工具类解析课表数据
        List<CourseSchedule> schedules = CourseScheduleParser.parse(rawData, userId, studentId);

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
}