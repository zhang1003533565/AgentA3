package com.example.appbackend.entity;

import com.example.appbackend.dto.StudyGoalDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudySubtaskPersistenceContractTest {

    @Test
    void subtaskContainsIndependentExecutionFields() throws Exception {
        Table table = StudySubtask.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("study_subtask", table.name());

        assertColumn("taskId", "task_id");
        assertColumn("taskName", "task_name");
        assertColumn("estimatedDays", "estimated_days");
        assertColumn("plannedStartDate", "planned_start_date");
        assertColumn("plannedEndDate", "planned_end_date");
        assertColumn("progressPercent", "progress_percent");
        assertColumn("orderNum", "order_num");

        assertEquals(LocalDate.class, StudySubtask.class.getDeclaredField("plannedStartDate").getType());
        assertEquals(LocalDate.class, StudySubtask.class.getDeclaredField("plannedEndDate").getType());
        assertEquals(Integer.class, StudySubtask.class.getDeclaredField("progressPercent").getType());
        assertEquals(0, new StudySubtask().getProgressPercent());
    }

    @Test
    void aiAndExternalTaskContractsExposeNestedSubtasks() throws Exception {
        Field agentSubtasks = StudyGoalDTO.AgentTask.class.getDeclaredField("subtasks");
        Field inputSubtasks = StudyGoalDTO.TaskInput.class.getDeclaredField("subtasks");
        Field viewSubtasks = StudyGoalDTO.TaskView.class.getDeclaredField("subtasks");
        assertEquals(List.class, agentSubtasks.getType());
        assertEquals(List.class, inputSubtasks.getType());
        assertEquals(List.class, viewSubtasks.getType());
    }

    private void assertColumn(String fieldName, String columnName) throws Exception {
        Column column = StudySubtask.class.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals(columnName, column.name());
    }
}
