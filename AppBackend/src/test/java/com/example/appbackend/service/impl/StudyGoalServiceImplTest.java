package com.example.appbackend.service.impl;

import com.example.appbackend.dto.StudyGoalDTO;
import com.example.appbackend.entity.StudyGoal;
import com.example.appbackend.entity.StudyTask;
import com.example.appbackend.entity.StudySubtask;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.StudyGoalRepository;
import com.example.appbackend.repository.StudyTaskRepository;
import com.example.appbackend.repository.StudySubtaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyGoalServiceImplTest {

    @Mock
    private PythonAiProxyService pythonAiProxyService;

    @Mock
    private StudyGoalRepository studyGoalRepository;

    @Mock
    private StudyTaskRepository studyTaskRepository;

    @Mock
    private StudySubtaskRepository studySubtaskRepository;

    @Test
    void saveGoalSchedulesTasksSequentiallyFromStartDate() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(invocation -> {
            StudyGoal saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(goal.getId());
            }
            return saved;
        });
        when(studyTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGoalDTO.GoalInput goalInput = new StudyGoalDTO.GoalInput();
        goalInput.setTitle("Java 基础");
        goalInput.setStartDate(LocalDate.of(2026, 8, 27));
        StudyGoalDTO.TaskInput first = task("语法", 2);
        StudyGoalDTO.TaskInput second = task("集合", 3);
        StudyGoalDTO.SaveRequest request = new StudyGoalDTO.SaveRequest();
        request.setGoal(goalInput);
        request.setTasks(List.of(first, second));

        StudyGoalServiceImpl service = service();
        StudyGoalDTO.GoalDetail detail = service.saveGoal(7L, request);

        assertEquals(LocalDate.of(2026, 8, 27), detail.getGoal().getStartDate());
        assertEquals(60, detail.getGoal().getDailyStudyMinutes());
        assertEquals(LocalDate.of(2026, 8, 27), detail.getTasks().get(0).getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 28), detail.getTasks().get(0).getPlannedEndDate());
        assertEquals(LocalDate.of(2026, 8, 29), detail.getTasks().get(1).getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), detail.getTasks().get(1).getPlannedEndDate());
    }

    @Test
    void saveGoalPersistsNestedSubtasksAndAggregatesProgressByLeafDays() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(invocation -> {
            StudyGoal saved = invocation.getArgument(0);
            saved.setId(goal.getId());
            return saved;
        });
        when(studyTaskRepository.saveAll(any())).thenAnswer(invocation -> {
            List<StudyTask> saved = invocation.getArgument(0);
            for (int index = 0; index < saved.size(); index++) {
                saved.get(index).setId(100L + index);
            }
            return saved;
        });
        AtomicReference<List<StudySubtask>> savedSubtasks = new AtomicReference<>(new ArrayList<>());
        when(studySubtaskRepository.saveAll(any())).thenAnswer(invocation -> {
            List<StudySubtask> saved = invocation.getArgument(0);
            for (int index = 0; index < saved.size(); index++) {
                saved.get(index).setId(200L + index);
            }
            savedSubtasks.set(saved);
            return saved;
        });
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(100L))
                .thenAnswer(invocation -> savedSubtasks.get());

        StudyGoalDTO.GoalInput goalInput = new StudyGoalDTO.GoalInput();
        goalInput.setTitle("Java 基础");
        goalInput.setStartDate(LocalDate.of(2026, 8, 27));
        StudyGoalDTO.TaskInput parent = task("语法", 99);
        parent.setSubtasks(List.of(subtask("阅读语法", 2), subtask("完成练习", 1)));
        StudyGoalDTO.SaveRequest request = new StudyGoalDTO.SaveRequest();
        request.setGoal(goalInput);
        request.setTasks(List.of(parent));

        StudyGoalDTO.GoalDetail detail = service().saveGoal(7L, request);

        StudyGoalDTO.TaskView savedParent = detail.getTasks().get(0);
        assertEquals(3, savedParent.getEstimatedDays());
        assertEquals(0, detail.getGoal().getProgress());
        assertEquals(2, savedParent.getSubtasks().size());
        assertEquals(LocalDate.of(2026, 8, 27), savedParent.getSubtasks().get(0).getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 28), savedParent.getSubtasks().get(0).getPlannedEndDate());
        assertEquals(LocalDate.of(2026, 8, 29), savedParent.getSubtasks().get(1).getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 29), savedParent.getSubtasks().get(1).getPlannedEndDate());
    }

    @Test
    void partialTaskProgressUsesEstimatedDaysAsWeight() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        StudyTask shortTask = taskEntity(1L, 1, 100, "completed");
        StudyTask longTask = taskEntity(2L, 3, 0, "pending");
        when(studyTaskRepository.findById(1L)).thenReturn(Optional.of(shortTask));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L))
                .thenReturn(List.of(shortTask, longTask));
        when(studyTaskRepository.save(any(StudyTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGoalServiceImpl service = service();
        service.updateTaskProgress(1L, 50, 7L);

        assertEquals(13, goal.getProgress());
        assertEquals("in_progress", goal.getStatus());
    }

    @Test
    void parentProgressUpdatesAllNestedSubtasksAndAggregatesLeafProgress() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        StudyTask parent = taskEntity(1L, 3, 0, "pending");
        StudySubtask first = subtaskEntity(11L, 1L, 1, 0, "pending");
        StudySubtask second = subtaskEntity(12L, 1L, 2, 0, "pending");
        List<StudySubtask> subtasks = new ArrayList<>(List.of(first, second));
        when(studyTaskRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(1L)).thenReturn(subtasks);
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(List.of(parent));
        when(studyTaskRepository.save(any(StudyTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studySubtaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGoalServiceImpl service = service();
        service.updateTaskProgress(1L, 50, 7L);

        assertEquals(50, first.getProgressPercent());
        assertEquals(50, second.getProgressPercent());
        assertEquals(50, parent.getProgressPercent());
        assertEquals(50, goal.getProgress());
        assertEquals("in_progress", goal.getStatus());
    }

    @Test
    void postponingTaskShiftsLaterUnfinishedTasksOnly() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        goal.setStartDate(LocalDate.of(2026, 8, 27));
        StudyTask selected = taskEntity(1L, 1, 0, "pending");
        selected.setPlannedStartDate(LocalDate.of(2026, 8, 27));
        selected.setPlannedEndDate(LocalDate.of(2026, 8, 27));
        StudyTask later = taskEntity(2L, 2, 0, "pending");
        later.setPlannedStartDate(LocalDate.of(2026, 8, 28));
        later.setPlannedEndDate(LocalDate.of(2026, 8, 29));
        StudyTask completed = taskEntity(3L, 1, 100, "completed");
        completed.setPlannedStartDate(LocalDate.of(2026, 8, 30));
        completed.setPlannedEndDate(LocalDate.of(2026, 8, 30));
        List<StudyTask> tasks = List.of(selected, later, completed);
        when(studyTaskRepository.findById(1L)).thenReturn(Optional.of(selected));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(tasks);
        when(studyTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGoalServiceImpl service = service();
        service.postponeTask(1L, 2, 7L);

        assertEquals(LocalDate.of(2026, 8, 29), selected.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 29), selected.getPlannedEndDate());
        assertEquals(LocalDate.of(2026, 8, 30), later.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), later.getPlannedEndDate());
        assertEquals(LocalDate.of(2026, 8, 30), completed.getPlannedStartDate());
    }

    @Test
    void oversizedDecomposeInputIsRejectedInsteadOfSilentlyTruncated() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "plan.csv", "text/csv", "x".repeat(16_001).getBytes(StandardCharsets.UTF_8));

        StudyGoalServiceImpl service = service();

        assertThrows(BusinessException.class, () -> service.decompose(7L, file, null, null));
        verifyNoInteractions(pythonAiProxyService);
    }

    private StudyGoalServiceImpl service() {
        return new StudyGoalServiceImpl(
                pythonAiProxyService, studyGoalRepository, studyTaskRepository, studySubtaskRepository,
                new ObjectMapper());
    }

    private StudyGoalDTO.TaskInput task(String name, int days) {
        StudyGoalDTO.TaskInput input = new StudyGoalDTO.TaskInput();
        input.setTaskName(name);
        input.setEstimatedDays(days);
        return input;
    }

    private StudyGoalDTO.SubtaskInput subtask(String name, int days) {
        StudyGoalDTO.SubtaskInput input = new StudyGoalDTO.SubtaskInput();
        input.setTaskName(name);
        input.setEstimatedDays(days);
        return input;
    }

    private StudyTask taskEntity(Long id, int days, int progress, String status) {
        StudyTask task = new StudyTask();
        task.setId(id);
        task.setGoalId(42L);
        task.setOrderNum(id.intValue());
        task.setTaskName("任务" + id);
        task.setEstimatedDays(days);
        task.setProgressPercent(progress);
        task.setIsCompleted(progress >= 100);
        task.setStatus(status);
        return task;
    }

    private StudySubtask subtaskEntity(Long id, Long taskId, int order, int progress, String status) {
        StudySubtask subtask = new StudySubtask();
        subtask.setId(id);
        subtask.setTaskId(taskId);
        subtask.setOrderNum(order);
        subtask.setTaskName("细分任务" + id);
        subtask.setEstimatedDays(1);
        subtask.setProgressPercent(progress);
        subtask.setIsCompleted(progress >= 100);
        subtask.setStatus(status);
        return subtask;
    }
}
