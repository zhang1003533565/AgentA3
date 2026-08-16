package com.example.appbackend.util;

import com.example.appbackend.dto.ScheduleChangeSummary;
import com.example.appbackend.entity.CourseSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseScheduleChangeAnalyzerTest {

    @Test
    void compareReportsAddedRemovedAndUpdatedCourses() {
        CourseSchedule oldMath = course("高等数学", "MATH-1", "王老师", "A101", 1, "1-2节", "1-16周");
        CourseSchedule oldEnglish = course("大学英语", "ENG-1", "李老师", "B201", 2, "3-4节", "1-16周");

        CourseSchedule newMath = course("高等数学", "MATH-1", "王老师", "A102", 1, "1-2节", "1-16周");
        CourseSchedule newPhysics = course("大学物理", "PHY-1", "赵老师", "C301", 3, "5-6节", "1-12周");

        ScheduleChangeSummary summary = CourseScheduleChangeAnalyzer.compare(
                List.of(oldMath, oldEnglish),
                List.of(newMath, newPhysics)
        );

        assertTrue(summary.getHasChanges());
        assertEquals(1, summary.getAddedCount());
        assertEquals(1, summary.getRemovedCount());
        assertEquals(1, summary.getUpdatedCount());
        assertEquals("大学物理", summary.getAdded().getFirst().getCourseName());
        assertEquals("大学英语", summary.getRemoved().getFirst().getCourseName());
        assertEquals("高等数学", summary.getUpdated().getFirst().getCourseName());
        assertEquals(List.of("上课地点"), summary.getUpdated().getFirst().getChangedFields());
    }

    @Test
    void compareReportsUnchangedCourses() {
        CourseSchedule oldMath = course("高等数学", "MATH-1", "王老师", "A101", 1, "1-2节", "1-16周");
        CourseSchedule newMath = course("高等数学", "MATH-1", "王老师", "A101", 1, "1-2节", "1-16周");

        ScheduleChangeSummary summary = CourseScheduleChangeAnalyzer.compare(List.of(oldMath), List.of(newMath));

        assertEquals(1, summary.getUnchangedCount());
        assertEquals(0, summary.getAddedCount());
        assertEquals(0, summary.getRemovedCount());
        assertEquals(0, summary.getUpdatedCount());
        assertEquals(false, summary.getHasChanges());
    }

    private static CourseSchedule course(
            String name,
            String classCode,
            String teacher,
            String location,
            Integer weekday,
            String sessions,
            String weekRange) {
        CourseSchedule schedule = new CourseSchedule();
        schedule.setCourseName(name);
        schedule.setClassCode(classCode);
        schedule.setTeacherName(teacher);
        schedule.setLocation(location);
        schedule.setWeekday(weekday);
        schedule.setClassSessions(sessions);
        schedule.setWeekRange(weekRange);
        return schedule;
    }
}
