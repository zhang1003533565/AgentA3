package com.example.appbackend.util;

import com.example.appbackend.dto.ScheduleChangeSummary;
import com.example.appbackend.entity.CourseSchedule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CourseScheduleChangeAnalyzer {

    private CourseScheduleChangeAnalyzer() {
    }

    public static ScheduleChangeSummary compare(List<CourseSchedule> oldSchedules, List<CourseSchedule> newSchedules) {
        List<CourseSnapshot> oldItems = toSnapshots(oldSchedules);
        List<CourseSnapshot> newItems = toSnapshots(newSchedules);
        List<CourseSnapshot> newUnmatched = new ArrayList<>(newItems);
        List<CourseSnapshot> oldUnmatched = new ArrayList<>();
        int unchangedCount = 0;

        for (CourseSnapshot oldItem : oldItems) {
            int exactIndex = firstIndexOfExactMatch(newUnmatched, oldItem);
            if (exactIndex >= 0) {
                newUnmatched.remove(exactIndex);
                unchangedCount++;
            } else {
                oldUnmatched.add(oldItem);
            }
        }

        List<ScheduleChangeSummary.CourseChangeItem> removed = new ArrayList<>();
        List<ScheduleChangeSummary.CourseChangeItem> updated = new ArrayList<>();
        for (CourseSnapshot oldItem : oldUnmatched) {
            int stableIndex = firstIndexOfStableMatch(newUnmatched, oldItem);
            if (stableIndex >= 0) {
                CourseSnapshot newItem = newUnmatched.remove(stableIndex);
                updated.add(toUpdatedItem(oldItem, newItem));
            } else {
                removed.add(toChangeItem(oldItem, List.of()));
            }
        }

        List<ScheduleChangeSummary.CourseChangeItem> added = newUnmatched.stream()
                .map(item -> toChangeItem(item, List.of()))
                .toList();

        return ScheduleChangeSummary.of(
                oldItems.size(),
                newItems.size(),
                unchangedCount,
                added,
                removed,
                updated
        );
    }

    private static List<CourseSnapshot> toSnapshots(List<CourseSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return List.of();
        }
        return schedules.stream().map(CourseSnapshot::from).toList();
    }

    private static int firstIndexOfExactMatch(List<CourseSnapshot> items, CourseSnapshot target) {
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(items.get(i).exactKey(), target.exactKey())) {
                return i;
            }
        }
        return -1;
    }

    private static int firstIndexOfStableMatch(List<CourseSnapshot> items, CourseSnapshot target) {
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(items.get(i).stableKey(), target.stableKey())) {
                return i;
            }
        }
        return -1;
    }

    private static ScheduleChangeSummary.CourseChangeItem toChangeItem(CourseSnapshot item, List<String> changedFields) {
        return new ScheduleChangeSummary.CourseChangeItem(
                item.courseName(),
                item.classCode(),
                item.summary(),
                changedFields
        );
    }

    private static ScheduleChangeSummary.CourseChangeItem toUpdatedItem(CourseSnapshot oldItem, CourseSnapshot newItem) {
        List<String> changedFields = new ArrayList<>();
        addChangedField(changedFields, "教师", oldItem.teacherName(), newItem.teacherName());
        addChangedField(changedFields, "上课地点", oldItem.location(), newItem.location());
        addChangedField(changedFields, "星期", oldItem.weekdayText(), newItem.weekdayText());
        addChangedField(changedFields, "节次", oldItem.classSessions(), newItem.classSessions());
        addChangedField(changedFields, "周次", oldItem.weekRange(), newItem.weekRange());
        addChangedField(changedFields, "校区", oldItem.campus(), newItem.campus());
        addChangedField(changedFields, "教学班", oldItem.classCode(), newItem.classCode());
        addChangedField(changedFields, "考核方式", oldItem.assessmentType(), newItem.assessmentType());
        addChangedField(changedFields, "学分", oldItem.creditText(), newItem.creditText());
        return toChangeItem(newItem, changedFields);
    }

    private static void addChangedField(List<String> fields, String label, String oldValue, String newValue) {
        if (!Objects.equals(normalizeText(oldValue), normalizeText(newValue))) {
            fields.add(label);
        }
    }

    private static String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String weekdayText(Integer weekday) {
        if (weekday == null || weekday < 1 || weekday > 7) {
            return "";
        }
        return "周" + "一二三四五六日".charAt(weekday - 1);
    }

    private record CourseSnapshot(
            String courseName,
            String classCode,
            String teacherName,
            String location,
            String campus,
            Integer weekday,
            String classSessions,
            String weekRange,
            String assessmentType,
            String creditText
    ) {
        private static CourseSnapshot from(CourseSchedule schedule) {
            return new CourseSnapshot(
                    normalizeText(schedule.getCourseName()),
                    normalizeText(schedule.getClassCode()),
                    normalizeText(schedule.getTeacherName()),
                    normalizeText(schedule.getLocation()),
                    normalizeText(schedule.getCampus()),
                    schedule.getWeekday(),
                    normalizeText(schedule.getClassSessions()),
                    normalizeText(schedule.getWeekRange()),
                    normalizeText(schedule.getAssessmentType()),
                    normalizeCredit(schedule.getCredit())
            );
        }

        private String stableKey() {
            String stableCode = classCode.isEmpty() ? courseName : classCode;
            return courseName + "|" + stableCode;
        }

        private String exactKey() {
            return stableKey() + "|"
                    + teacherName + "|"
                    + location + "|"
                    + campus + "|"
                    + weekdayText() + "|"
                    + classSessions + "|"
                    + weekRange + "|"
                    + assessmentType + "|"
                    + creditText;
        }

        private String weekdayText() {
            return CourseScheduleChangeAnalyzer.weekdayText(weekday);
        }

        private String summary() {
            List<String> parts = new ArrayList<>();
            parts.add(courseName);
            if (!weekdayText().isEmpty() || !classSessions.isEmpty()) {
                parts.add((weekdayText() + " " + classSessions).trim());
            }
            if (!location.isEmpty()) {
                parts.add(location);
            }
            return String.join(" · ", parts);
        }

        private static String normalizeCredit(BigDecimal credit) {
            if (credit == null) {
                return "";
            }
            return credit.stripTrailingZeros().toPlainString();
        }
    }
}
