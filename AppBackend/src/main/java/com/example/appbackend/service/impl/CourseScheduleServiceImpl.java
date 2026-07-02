package com.example.appbackend.service.impl;

import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.CourseScheduleRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.util.CourseScheduleParser;
import com.example.appbackend.util.WeekCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseScheduleServiceImpl implements CourseScheduleService {
    private static final Logger log = LoggerFactory.getLogger(CourseScheduleServiceImpl.class);

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
        log.info("课表解析完成，userId={}, studentId={}, 解析到有效课程数={}", userId, studentId, schedules.size());

        // 保存新课表
        courseScheduleRepository.saveAll(schedules);
        log.info("课表保存完成，userId={}, 保存课程数={}", userId, schedules.size());
    }

    @Override
    @Transactional
    public void saveSchedule(Long userId, String studentId, String rawData, String academicYear, Integer semesterTerm, String semesterCode) {
        courseScheduleRepository.deleteByUserIdAndSemester(userId, academicYear, semesterTerm);

        List<CourseSchedule> schedules = CourseScheduleParser.parse(rawData, userId, studentId);
        schedules.forEach(schedule -> {
            schedule.setAcademicYear(academicYear);
            schedule.setSemesterTerm(semesterTerm);
            schedule.setSemesterCode(semesterCode);
        });
        log.info("指定学期课表解析完成，userId={}, studentId={}, academicYear={}, semesterTerm={}, 解析到有效课程数={}",
                userId, studentId, academicYear, semesterTerm, schedules.size());

        courseScheduleRepository.saveAll(schedules);
        log.info("指定学期课表保存完成，userId={}, academicYear={}, semesterTerm={}, 保存课程数={}",
                userId, academicYear, semesterTerm, schedules.size());
    }

    @Override
    public List<CourseSchedule> getUserSchedule(Long userId) {
        return courseScheduleRepository.findByUserId(userId);
    }

    @Override
    public List<CourseSchedule> getUserSchedule(Long userId, String academicYear, Integer semesterTerm) {
        if (academicYear == null || academicYear.trim().isEmpty() || semesterTerm == null) {
            return getUserSchedule(userId);
        }
        return courseScheduleRepository.findByUserIdAndAcademicYearAndSemesterTerm(userId, academicYear.trim(), semesterTerm);
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
        log.info("计算当前周次，userId={}, semesterStart={}, currentWeek={}", userId, user.getSemesterStart(), currentWeek);

        if (currentWeek <= 0) {
            log.warn("当前周课表为空：学期未开始或未设置开学日期，userId={}, semesterStart={}, currentWeek={}",
                    userId, user.getSemesterStart(), currentWeek);
            return List.of();
        }

        return getWeekSchedule(userId, currentWeek);
    }

    @Override
    public List<CourseSchedule> getCurrentWeekSchedule(Long userId, java.time.LocalDate semesterStart, String academicYear, Integer semesterTerm) {
        int currentWeek = WeekCalculator.getCurrentWeek(semesterStart);
        log.info("按指定学期计算当前周次，userId={}, academicYear={}, semesterTerm={}, semesterStart={}, currentWeek={}",
                userId, academicYear, semesterTerm, semesterStart, currentWeek);

        if (currentWeek <= 0) {
            return List.of();
        }

        return getWeekSchedule(userId, currentWeek, academicYear, semesterTerm);
    }

    @Override
    public List<CourseSchedule> getWeekSchedule(Long userId, int week) {
        // 获取用户的所有课表
        List<CourseSchedule> allSchedules = courseScheduleRepository.findByUserId(userId);
        log.info("按周查询课表，userId={}, week={}, 全部课程数={}", userId, week, allSchedules.size());

        // 过滤出指定周次的课程
        List<CourseSchedule> result = allSchedules.stream()
                .filter(schedule -> {
                    String weekRange = schedule.getWeekRange();
                    boolean isInWeek = WeekCalculator.isWeekInRange(weekRange, week);
                    return isInWeek;
                })
                .collect(Collectors.toList());
        log.info("按周查询结果，userId={}, week={}, 命中课程数={}", userId, week, result.size());
        return result;
    }

    @Override
    public List<CourseSchedule> getWeekSchedule(Long userId, int week, String academicYear, Integer semesterTerm) {
        List<CourseSchedule> allSchedules = getUserSchedule(userId, academicYear, semesterTerm);
        log.info("按学期周次查询课表，userId={}, academicYear={}, semesterTerm={}, week={}, 学期课程数={}",
                userId, academicYear, semesterTerm, week, allSchedules.size());

        List<CourseSchedule> result = allSchedules.stream()
                .filter(schedule -> WeekCalculator.isWeekInRange(schedule.getWeekRange(), week))
                .collect(Collectors.toList());
        log.info("按学期周次查询结果，userId={}, academicYear={}, semesterTerm={}, week={}, 命中课程数={}",
                userId, academicYear, semesterTerm, week, result.size());
        return result;
    }

    @Override
    public CourseSchedule getCourseDetail(Long userId, Long courseId) {
        CourseSchedule schedule = courseScheduleRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("课程不存在"));

        // 验证课程是否属于该用户
        if (!schedule.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看该课程信息");
        }

        return schedule;
    }

    @Override
    @Transactional
    public void copyScheduleByShareCode(Long userId, String shareCode) {
        copyScheduleByShareCode(userId, shareCode, null, null);
    }

    @Override
    @Transactional
    public void copyScheduleByShareCode(Long userId, String shareCode, String academicYear, Integer semesterTerm) {
        // 1. 根据分享码查找用户
        User sourceUser = userRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享码无效"));

        // 不能复制自己的课表
        if (sourceUser.getId().equals(userId)) {
            throw new RuntimeException("不能复制自己的课表");
        }

        boolean scopedCopy = academicYear != null && !academicYear.trim().isEmpty() && semesterTerm != null;
        String targetAcademicYear = scopedCopy ? academicYear.trim() : null;
        Integer targetSemesterTerm = scopedCopy ? semesterTerm : null;

        // 2. 获取分享者的课表
        List<CourseSchedule> sourceSchedules = scopedCopy
                ? courseScheduleRepository.findByUserIdAndAcademicYearAndSemesterTerm(sourceUser.getId(), targetAcademicYear, targetSemesterTerm)
                : courseScheduleRepository.findByUserId(sourceUser.getId());

        if (scopedCopy && (sourceSchedules == null || sourceSchedules.isEmpty())) {
            List<CourseSchedule> legacySchedules = courseScheduleRepository.findByUserId(sourceUser.getId()).stream()
                    .filter(schedule -> schedule.getAcademicYear() == null && schedule.getSemesterTerm() == null)
                    .collect(Collectors.toList());
            if (!legacySchedules.isEmpty()) {
                sourceSchedules = legacySchedules;
            }
        }

        if (sourceSchedules == null || sourceSchedules.isEmpty()) {
            throw new RuntimeException(scopedCopy ? "该用户本学期课表为空" : "该用户的课表为空");
        }

        // 3. 只覆盖目标学期；未指定学期时保留旧行为，覆盖全部课表
        if (scopedCopy) {
            courseScheduleRepository.deleteByUserIdAndSemester(userId, targetAcademicYear, targetSemesterTerm);
        } else {
            courseScheduleRepository.deleteByUserId(userId);
        }

        User targetUser = userRepository.findById(userId).orElse(null);
        String targetStudentId = targetUser != null ? targetUser.getPersonalNumber() : null;
        String copiedStudentId = targetStudentId != null ? targetStudentId : sourceUser.getPersonalNumber();

        // 4. 复制课表数据，设置为目标用户的课表
        List<CourseSchedule> copiedSchedules = sourceSchedules.stream().map(schedule -> {
            CourseSchedule newSchedule = new CourseSchedule();
            newSchedule.setUserId(userId);
            newSchedule.setStudentId(copiedStudentId);
            newSchedule.setAcademicYear(scopedCopy ? targetAcademicYear : schedule.getAcademicYear());
            newSchedule.setSemesterTerm(scopedCopy ? targetSemesterTerm : schedule.getSemesterTerm());
            newSchedule.setSemesterCode(scopedCopy ? semesterCodeForTerm(targetSemesterTerm) : schedule.getSemesterCode());
            newSchedule.setCourseName(schedule.getCourseName());
            newSchedule.setWeekRange(schedule.getWeekRange());
            newSchedule.setClassSessions(schedule.getClassSessions());
            newSchedule.setWeekday(schedule.getWeekday());
            newSchedule.setLocation(schedule.getLocation());
            newSchedule.setCampus(schedule.getCampus());
            newSchedule.setTeacherName(schedule.getTeacherName());
            newSchedule.setClassCode(schedule.getClassCode());
            newSchedule.setClassComposition(schedule.getClassComposition());
            newSchedule.setAssessmentType(schedule.getAssessmentType());
            newSchedule.setTheoryHours(schedule.getTheoryHours());
            newSchedule.setLabHours(schedule.getLabHours());
            newSchedule.setWeeklyHours(schedule.getWeeklyHours());
            newSchedule.setTotalHours(schedule.getTotalHours());
            newSchedule.setCredit(schedule.getCredit());
            return newSchedule;
        }).collect(Collectors.toList());

        log.info("复制课表：fromUser={}, toUser={}, academicYear={}, semesterTerm={}, count={}",
                sourceUser.getUsername(), userId, targetAcademicYear, targetSemesterTerm, copiedSchedules.size());

        // 5. 保存复制的课表
        courseScheduleRepository.saveAll(copiedSchedules);
    }

    private String semesterCodeForTerm(Integer semesterTerm) {
        return Integer.valueOf(2).equals(semesterTerm) ? "12" : "3";
    }
}
