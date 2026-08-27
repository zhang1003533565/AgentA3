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
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
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
    void todayDetailFilterOnlyReturnsSubtasksScheduledForToday() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        LocalDate today = LocalDate.now();
        StudyTask parent = taskEntity(1L, 2, 0, "pending");
        parent.setPlannedStartDate(today);
        parent.setPlannedEndDate(today.plusDays(1));
        StudySubtask todaySubtask = subtaskEntity(11L, 1L, 1, 0, "pending");
        todaySubtask.setPlannedStartDate(today);
        todaySubtask.setPlannedEndDate(today);
        StudySubtask futureSubtask = subtaskEntity(12L, 1L, 2, 0, "pending");
        futureSubtask.setPlannedStartDate(today.plusDays(1));
        futureSubtask.setPlannedEndDate(today.plusDays(1));

        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(List.of(parent));
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(1L))
                .thenReturn(List.of(todaySubtask, futureSubtask));

        StudyGoalDTO.GoalDetail detail = service().getGoalDetail(42L, 7L, "today");

        assertEquals(1, detail.getTasks().size());
        assertEquals(1, detail.getTasks().get(0).getSubtasks().size());
        assertEquals(11L, detail.getTasks().get(0).getSubtasks().get(0).getId());
    }

    @Test
    void subtaskProgressUpdatesOnlyTheLeafAndRecomputesParentAndGoal() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        StudyTask parent = taskEntity(1L, 3, 0, "pending");
        StudySubtask first = subtaskEntity(11L, 1L, 1, 0, "pending");
        StudySubtask second = subtaskEntity(12L, 1L, 2, 0, "pending");
        List<StudySubtask> subtasks = new ArrayList<>(List.of(first, second));
        when(studySubtaskRepository.findById(11L)).thenReturn(Optional.of(first));
        when(studyTaskRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(1L)).thenReturn(subtasks);
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(List.of(parent));
        when(studySubtaskRepository.save(any(StudySubtask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().updateSubtaskProgress(11L, 100, 7L);

        assertEquals(100, first.getProgressPercent());
        assertEquals(0, second.getProgressPercent());
        assertEquals(50, parent.getProgressPercent());
        assertEquals(50, goal.getProgress());
    }

    @Test
    void remainingTasksOnlyExposeUnfinishedNestedSubtasks() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        StudyTask parent = taskEntity(1L, 3, 50, "in_progress");
        StudySubtask completed = subtaskEntity(11L, 1L, 1, 100, "completed");
        StudySubtask pending = subtaskEntity(12L, 1L, 2, 0, "pending");

        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdAndIsCompletedFalseOrderByOrderNumAscIdAsc(42L))
                .thenReturn(List.of(parent));
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(1L))
                .thenReturn(List.of(completed, pending));

        List<StudyGoalDTO.TaskView> remaining = service().getRemainingTasks(42L, 7L);

        assertEquals(1, remaining.size());
        assertEquals(1, remaining.get(0).getSubtasks().size());
        assertEquals(12L, remaining.get(0).getSubtasks().get(0).getId());
    }

    @Test
    void postponingSubtaskShiftsLaterLeafItemsAndRefreshesParentWindow() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        goal.setStartDate(LocalDate.of(2026, 8, 27));
        StudyTask parent = taskEntity(1L, 3, 0, "pending");
        parent.setPlannedStartDate(LocalDate.of(2026, 8, 27));
        parent.setPlannedEndDate(LocalDate.of(2026, 8, 29));
        StudyTask later = taskEntity(2L, 1, 0, "pending");
        later.setPlannedStartDate(LocalDate.of(2026, 8, 30));
        later.setPlannedEndDate(LocalDate.of(2026, 8, 30));
        StudySubtask selected = subtaskEntity(11L, 1L, 1, 0, "pending");
        selected.setPlannedStartDate(LocalDate.of(2026, 8, 27));
        selected.setPlannedEndDate(LocalDate.of(2026, 8, 27));
        StudySubtask laterSubtask = subtaskEntity(12L, 1L, 2, 0, "pending");
        laterSubtask.setPlannedStartDate(LocalDate.of(2026, 8, 28));
        laterSubtask.setPlannedEndDate(LocalDate.of(2026, 8, 29));
        List<StudySubtask> subtasks = new ArrayList<>(List.of(selected, laterSubtask));
        when(studySubtaskRepository.findById(11L)).thenReturn(Optional.of(selected));
        when(studyTaskRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(List.of(parent, later));
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(1L)).thenReturn(subtasks);
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(2L)).thenReturn(List.of());
        when(studySubtaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGoalDTO.GoalDetail detail = service().postponeSubtask(11L, 1, 7L);

        assertEquals(LocalDate.of(2026, 8, 28), selected.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 29), laterSubtask.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 30), laterSubtask.getPlannedEndDate());
        assertEquals(LocalDate.of(2026, 8, 31), later.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 30), parent.getPlannedEndDate());
        assertEquals(2, detail.getTasks().size());
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
    void postponingNestedTaskShiftsItsUnfinishedSubtasksAndKeepsCompletedOnes() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        goal.setStartDate(LocalDate.of(2026, 8, 27));
        StudyTask selected = taskEntity(1L, 3, 0, "pending");
        selected.setPlannedStartDate(LocalDate.of(2026, 8, 27));
        selected.setPlannedEndDate(LocalDate.of(2026, 8, 29));
        StudyTask later = taskEntity(2L, 1, 0, "pending");
        later.setPlannedStartDate(LocalDate.of(2026, 8, 30));
        later.setPlannedEndDate(LocalDate.of(2026, 8, 30));

        StudySubtask first = subtaskEntity(11L, 1L, 1, 0, "pending");
        first.setPlannedStartDate(LocalDate.of(2026, 8, 27));
        first.setPlannedEndDate(LocalDate.of(2026, 8, 27));
        StudySubtask completed = subtaskEntity(12L, 1L, 2, 100, "completed");
        completed.setPlannedStartDate(LocalDate.of(2026, 8, 28));
        completed.setPlannedEndDate(LocalDate.of(2026, 8, 28));
        StudySubtask last = subtaskEntity(13L, 1L, 3, 0, "pending");
        last.setPlannedStartDate(LocalDate.of(2026, 8, 29));
        last.setPlannedEndDate(LocalDate.of(2026, 8, 29));
        List<StudySubtask> subtasks = new ArrayList<>(List.of(first, completed, last));
        List<StudyTask> tasks = List.of(selected, later);

        when(studyTaskRepository.findById(1L)).thenReturn(Optional.of(selected));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(tasks);
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(1L)).thenReturn(subtasks);
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(2L)).thenReturn(List.of());
        when(studySubtaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().postponeTask(1L, 1, 7L);

        assertEquals(LocalDate.of(2026, 8, 28), first.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 28), completed.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 30), last.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 28), selected.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 8, 30), selected.getPlannedEndDate());
        assertEquals(LocalDate.of(2026, 8, 31), later.getPlannedStartDate());
    }

    @Test
    void oversizedDecomposeInputIsRejectedInsteadOfSilentlyTruncated() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "plan.csv", "text/csv", "x".repeat(16_001).getBytes(StandardCharsets.UTF_8));

        StudyGoalServiceImpl service = service();

        assertThrows(BusinessException.class, () -> service.decompose(7L, file, null, null));
        verifyNoInteractions(pythonAiProxyService);
    }

    @Test
    void deleteGoalRemovesOwnedSubtasksTasksAndGoalInOrder() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        StudyTask first = taskEntity(1L, 1, 0, "pending");
        StudyTask second = taskEntity(2L, 2, 100, "completed");
        List<StudyTask> tasks = List.of(first, second);
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(tasks);

        service().deleteGoal(42L, 7L);

        InOrder order = inOrder(studySubtaskRepository, studyTaskRepository, studyGoalRepository);
        order.verify(studySubtaskRepository).deleteByTaskIdIn(List.of(1L, 2L));
        order.verify(studyTaskRepository).deleteAllInBatch(tasks);
        order.verify(studyGoalRepository).delete(goal);
    }

    @Test
    void deleteGoalRejectsAnotherUsersGoalBeforeTouchingChildren() {
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service().deleteGoal(42L, 7L));
        verifyNoInteractions(studyTaskRepository, studySubtaskRepository);
    }

    @Test
    void expandsOnlyLegacyTasksAndKeepsExistingSubtasks() {
        StudyGoal goal = new StudyGoal();
        goal.setId(42L);
        goal.setTitle("30天学会 C++");
        goal.setStartDate(LocalDate.of(2026, 8, 27));
        StudyTask legacy = taskEntity(1L, 3, 0, "pending");
        StudyTask existing = taskEntity(2L, 2, 50, "in_progress");
        StudySubtask existingSubtask = subtaskEntity(21L, 2L, 1, 50, "in_progress");
        List<StudyTask> tasks = List.of(legacy, existing);
        Map<Long, List<StudySubtask>> storedSubtasks = new HashMap<>();
        storedSubtasks.put(existing.getId(), new ArrayList<>(List.of(existingSubtask)));
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(goal));
        when(studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(42L)).thenReturn(tasks);
        when(studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(any(Long.class)))
                .thenAnswer(invocation -> storedSubtasks.getOrDefault(invocation.getArgument(0), List.of()));
        when(studySubtaskRepository.saveAll(any())).thenAnswer(invocation -> {
            List<StudySubtask> saved = invocation.getArgument(0);
            storedSubtasks.put(saved.get(0).getTaskId(), new ArrayList<>(saved));
            return saved;
        });
        when(studyTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pythonAiProxyService.generateGoalDecomposition(any(), any())).thenReturn(
                Map.of(
                        "goal", Map.of("title", "30天学会 C++", "description", ""),
                        "tasks", List.of(
                                Map.of(
                                        "task_name", "任务1",
                                        "estimated_days", 3,
                                        "subtasks", List.of(
                                                Map.of("task_name", "阅读资料", "description", "写出三条要点", "estimated_days", 1),
                                                Map.of("task_name", "完成练习", "description", "提交一份练习结果", "estimated_days", 2)
                                        )
                                ),
                                Map.of(
                                        "task_name", "任务2",
                                        "estimated_days", 2,
                                        "subtasks", List.of(
                                                Map.of("task_name", "不应覆盖", "description", "保留已有任务", "estimated_days", 2)
                                        )
                                )
                        )
                )
        );

        StudyGoalDTO.GoalDetail detail = service().expandMissingSubtasks(42L, 7L, "Bearer token");

        assertEquals(2, detail.getTasks().get(0).getSubtasks().size());
        assertEquals(1, detail.getTasks().get(1).getSubtasks().size());
        assertEquals(21L, detail.getTasks().get(1).getSubtasks().get(0).getId());
        verify(pythonAiProxyService).generateGoalDecomposition(any(), any());
    }

    @Test
    void expandingAnotherUsersGoalStopsBeforeCallingAi() {
        when(studyGoalRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service().expandMissingSubtasks(42L, 7L, "Bearer token"));
        verifyNoInteractions(pythonAiProxyService, studyTaskRepository, studySubtaskRepository);
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
