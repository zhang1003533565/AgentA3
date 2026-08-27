package com.example.appbackend.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudyGoalPersistenceContractTest {

    @Test
    void goalStoresOptionalPlanningWindow() throws Exception {
        assertColumn(StudyGoal.class, "startDate", "start_date");
        assertColumn(StudyGoal.class, "targetDate", "target_date");

        assertEquals(LocalDate.class, StudyGoal.class.getDeclaredField("startDate").getType());
        assertEquals(LocalDate.class, StudyGoal.class.getDeclaredField("targetDate").getType());
    }

    @Test
    void taskStoresScheduledWindowAndWeightedProgress() throws Exception {
        assertColumn(StudyTask.class, "plannedStartDate", "planned_start_date");
        assertColumn(StudyTask.class, "plannedEndDate", "planned_end_date");
        assertColumn(StudyTask.class, "progressPercent", "progress_percent");

        assertEquals(LocalDate.class, StudyTask.class.getDeclaredField("plannedStartDate").getType());
        assertEquals(LocalDate.class, StudyTask.class.getDeclaredField("plannedEndDate").getType());
        assertEquals(Integer.class, StudyTask.class.getDeclaredField("progressPercent").getType());
        assertEquals(0, new StudyTask().getProgressPercent());
    }

    private void assertColumn(Class<?> type, String fieldName, String columnName) throws Exception {
        Column column = type.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals(columnName, column.name());
    }
}
