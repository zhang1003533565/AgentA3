package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.StudyGoalDTO;
import com.example.appbackend.entity.StudyGoal;
import com.example.appbackend.entity.StudyTask;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.StudySubtask;
import com.example.appbackend.repository.StudyGoalRepository;
import com.example.appbackend.repository.StudyTaskRepository;
import com.example.appbackend.repository.StudySubtaskRepository;
import com.example.appbackend.service.StudyGoalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习计划结构化拆解服务实现。
 *
 * 拆解链路：xlsx/csv/text -> 表格序列化文本 -> ai-servers 专用智能体 -> 纯 JSON 预览。
 * Goal 进度由任务预计学习天数加权自动计算，任务状态与进度更新共用 applyProgress。
 */
@Slf4j
@Service
public class StudyGoalServiceImpl implements StudyGoalService {

    private static final int MAX_TASKS = 100;
    private static final int MAX_TABLE_ROWS = 300;
    private static final int MAX_TABLE_COLS = 30;
    private static final int MAX_CONTENT_CHARS = 16000;
    private static final int MAX_ESTIMATED_DAYS = 3650;
    private static final List<String> ALLOWED_PRIORITIES = List.of("高", "中", "低");
    private static final String DEFAULT_PRIORITY = "中";
    private static final String CELL_DELIMITER = " | ";

    private final PythonAiProxyService pythonAiProxyService;
    private final StudyGoalRepository studyGoalRepository;
    private final StudyTaskRepository studyTaskRepository;
    private final StudySubtaskRepository studySubtaskRepository;
    private final ObjectMapper objectMapper;

    public StudyGoalServiceImpl(PythonAiProxyService pythonAiProxyService,
                                StudyGoalRepository studyGoalRepository,
                                StudyTaskRepository studyTaskRepository,
                                StudySubtaskRepository studySubtaskRepository,
                                ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.studyGoalRepository = studyGoalRepository;
        this.studyTaskRepository = studyTaskRepository;
        this.studySubtaskRepository = studySubtaskRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public StudyGoalDTO.DecomposeResponse decompose(Long userId, MultipartFile file, String planText, String authorization) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasText = StringUtils.hasText(planText);
        if (hasFile == hasText) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请上传 .xlsx/.csv 数据表或粘贴学习计划文本（二选一）");
        }

        String sourceType;
        String content;
        if (hasFile) {
            sourceType = resolveTableSuffix(file.getOriginalFilename());
            content = "xlsx".equals(sourceType) ? extractXlsxText(file) : extractCsvText(file);
        } else {
            sourceType = "text";
            content = planText.trim();
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "文件内容为空或无法解析出任务数据");
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new BusinessException(Result.BAD_REQUEST_CODE,
                    "文件或文本内容过长，请控制在" + MAX_CONTENT_CHARS + "字符以内");
        }

        Map<String, Object> request = new HashMap<>();
        request.put("sourceType", sourceType);
        request.put("content", content);
        Object rawResult = pythonAiProxyService.generateGoalDecomposition(request, authorization);
        return toDecomposeResponse(rawResult);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalDetail saveGoal(Long userId, StudyGoalDTO.SaveRequest request) {
        if (request == null || request.getGoal() == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "缺少目标信息");
        }
        String title = StringUtils.trimWhitespace(request.getGoal().getTitle());
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "目标标题不能为空");
        }
        List<StudyGoalDTO.TaskInput> inputs = new ArrayList<>();
        if (request.getTasks() != null) {
            for (StudyGoalDTO.TaskInput input : request.getTasks()) {
                if (input != null && StringUtils.hasText(input.getTaskName())) {
                    inputs.add(input);
                }
                if (inputs.size() >= MAX_TASKS) {
                    break;
                }
            }
        }
        if (inputs.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "至少需要一条有效任务");
        }

        StudyGoal goal = new StudyGoal();
        goal.setUserId(userId);
        goal.setTitle(trimToLength(title, 120));
        goal.setDescription(StringUtils.trimWhitespace(request.getGoal().getDescription()));
        LocalDate startDate = request.getGoal().getStartDate() == null
                ? LocalDate.now() : request.getGoal().getStartDate();
        LocalDate targetDate = request.getGoal().getTargetDate();
        if (targetDate != null && targetDate.isBefore(startDate)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "目标日期不能早于开始日期");
        }
        goal.setStartDate(startDate);
        goal.setTargetDate(targetDate);
        goal.setDailyStudyMinutes(normalizeDailyStudyMinutes(request.getGoal().getDailyStudyMinutes()));
        goal.setProgress(0);
        goal.setStatus("pending");
        goal = studyGoalRepository.save(goal);

        List<StudyTask> tasks = new ArrayList<>();
        int orderNum = 1;
        for (StudyGoalDTO.TaskInput input : inputs) {
            StudyTask task = buildTaskEntity(goal.getId(), input, orderNum++);
            List<StudyGoalDTO.SubtaskInput> subtasks = validSubtaskInputs(input.getSubtasks());
            if (!subtasks.isEmpty()) {
                task.setEstimatedDays(sumEstimatedDays(subtasks));
            }
            tasks.add(task);
        }
        scheduleTasks(tasks, startDate);
        LocalDate scheduledEnd = tasks.get(tasks.size() - 1).getPlannedEndDate();
        if (targetDate != null && scheduledEnd.isAfter(targetDate)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "预计排期超过目标日期，请调整目标日期或任务天数");
        }
        tasks = studyTaskRepository.saveAll(tasks);
        List<StudySubtask> subtasks = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            List<StudyGoalDTO.SubtaskInput> inputsForTask = validSubtaskInputs(inputs.get(index).getSubtasks());
            if (inputsForTask.isEmpty()) {
                continue;
            }
            for (StudyGoalDTO.SubtaskInput input : inputsForTask) {
                subtasks.add(buildSubtaskEntity(tasks.get(index).getId(), input));
            }
            scheduleSubtasks(subtasks.subList(subtasks.size() - inputsForTask.size(), subtasks.size()),
                    tasks.get(index).getPlannedStartDate());
        }
        if (!subtasks.isEmpty()) {
            subtasks = studySubtaskRepository.saveAll(subtasks);
        }
        applyProgress(goal, tasks);

        StudyGoalDTO.GoalDetail detail = new StudyGoalDTO.GoalDetail();
        detail.setGoal(toGoalView(goal));
        for (StudyTask task : tasks) {
            detail.getTasks().add(toTaskView(task));
        }
        return detail;
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalView updateTaskCompletion(Long taskId, Boolean isCompleted, Long userId) {
        return updateTaskProgress(taskId, Boolean.TRUE.equals(isCompleted) ? 100 : 0, userId);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalView updateTaskProgress(Long taskId, Integer progressPercent, Long userId) {
        int normalizedProgress = normalizeProgress(progressPercent);
        StudyTask task = studyTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "任务不存在"));
        requireOwnedGoal(task.getGoalId(), userId);
        boolean completed = normalizedProgress >= 100;
        List<StudySubtask> subtasks = subtasksOf(task);
        if (subtasks.isEmpty()) {
            task.setProgressPercent(normalizedProgress);
            task.setIsCompleted(completed);
            task.setStatus(completed ? "completed" : normalizedProgress > 0 ? "in_progress" : "pending");
        } else {
            applyProgressToSubtasks(subtasks, normalizedProgress);
            studySubtaskRepository.saveAll(subtasks);
        }
        studyTaskRepository.save(task);

        StudyGoal goal = requireOwnedGoal(task.getGoalId(), userId);
        applyProgress(goal, studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goal.getId()));
        return toGoalView(goal);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalView updateTaskStatus(Long taskId, String status, Long userId) {
        validateTaskStatus(status);
        StudyTask task = studyTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "任务不存在"));
        requireOwnedGoal(task.getGoalId(), userId);
        List<StudySubtask> subtasks = subtasksOf(task);
        if (subtasks.isEmpty()) {
            task.setStatus(status);
            if ("completed".equals(status)) {
                task.setProgressPercent(100);
                task.setIsCompleted(true);
            } else {
                task.setIsCompleted(false);
                if ("pending".equals(status) && effectiveProgress(task) >= 100) {
                    task.setProgressPercent(0);
                }
            }
        } else {
            int progress = "completed".equals(status) ? 100 : "pending".equals(status) ? 0 : effectiveProgress(task);
            applyProgressToSubtasks(subtasks, progress);
            subtasks.forEach(subtask -> subtask.setStatus(status));
            studySubtaskRepository.saveAll(subtasks);
        }
        studyTaskRepository.save(task);
        StudyGoal goal = requireOwnedGoal(task.getGoalId(), userId);
        applyProgress(goal, studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goal.getId()));
        return toGoalView(goal);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalView updateSubtaskCompletion(Long subtaskId, Boolean isCompleted, Long userId) {
        return updateSubtaskProgress(subtaskId, Boolean.TRUE.equals(isCompleted) ? 100 : 0, userId);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalView updateSubtaskProgress(Long subtaskId, Integer progressPercent, Long userId) {
        int normalizedProgress = normalizeProgress(progressPercent);
        StudySubtask subtask = requireOwnedSubtask(subtaskId, userId);
        boolean completed = normalizedProgress >= 100;
        subtask.setProgressPercent(normalizedProgress);
        subtask.setIsCompleted(completed);
        subtask.setStatus(completed ? "completed" : normalizedProgress > 0 ? "in_progress" : "pending");
        studySubtaskRepository.save(subtask);
        return refreshGoalAfterSubtaskChange(subtask.getTaskId(), userId);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalView updateSubtaskStatus(Long subtaskId, String status, Long userId) {
        validateTaskStatus(status);
        StudySubtask subtask = requireOwnedSubtask(subtaskId, userId);
        subtask.setStatus(status);
        if ("completed".equals(status)) {
            subtask.setProgressPercent(100);
            subtask.setIsCompleted(true);
        } else {
            subtask.setIsCompleted(false);
            if ("pending".equals(status) && effectiveProgress(subtask) >= 100) {
                subtask.setProgressPercent(0);
            }
        }
        studySubtaskRepository.save(subtask);
        return refreshGoalAfterSubtaskChange(subtask.getTaskId(), userId);
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalDetail postponeTask(Long taskId, Integer days, Long userId) {
        if (days == null || days < 1 || days > 30) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "延后天数必须在 1 到 30 天之间");
        }
        StudyTask selected = studyTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "任务不存在"));
        StudyGoal goal = requireOwnedGoal(selected.getGoalId(), userId);
        if (effectiveProgress(selected) >= 100 || "completed".equals(selected.getStatus())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "已完成任务不能延后");
        }
        int selectedOrder = selected.getOrderNum() == null ? Integer.MAX_VALUE : selected.getOrderNum();
        LocalDate selectedStart = selected.getPlannedStartDate() == null
                ? (goal.getStartDate() == null ? LocalDate.now() : goal.getStartDate())
                : selected.getPlannedStartDate();
        LocalDate selectedEnd = selected.getPlannedEndDate() == null
                ? selectedStart.plusDays(normalizeEstimatedDays(selected.getEstimatedDays()) - 1L)
                : selected.getPlannedEndDate();
        List<StudyTask> tasks = studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goal.getId());
        List<StudyTask> changed = new ArrayList<>();
        for (StudyTask task : tasks) {
            int order = task.getOrderNum() == null ? Integer.MAX_VALUE : task.getOrderNum();
            if (order < selectedOrder || effectiveProgress(task) >= 100 || "completed".equals(task.getStatus())) {
                continue;
            }
            LocalDate start = task == selected ? selectedStart : task.getPlannedStartDate();
            LocalDate end = task == selected ? selectedEnd : task.getPlannedEndDate();
            if (start == null || end == null) {
                continue;
            }
            task.setPlannedStartDate(start.plusDays(days));
            task.setPlannedEndDate(end.plusDays(days));
            changed.add(task);
        }
        if (!changed.contains(selected)) {
            selected.setPlannedStartDate(selectedStart.plusDays(days));
            selected.setPlannedEndDate(selectedEnd.plusDays(days));
            changed.add(selected);
        }
        studyTaskRepository.saveAll(changed);
        return getGoalDetail(goal.getId(), userId, "all");
    }

    @Override
    @Transactional
    public StudyGoalDTO.GoalDetail postponeSubtask(Long subtaskId, Integer days, Long userId) {
        validatePostponeDays(days);
        StudySubtask selected = studySubtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "细分任务不存在"));
        StudyTask selectedParent = requireOwnedSubtaskParent(selected, userId);
        StudyGoal goal = requireOwnedGoal(selectedParent.getGoalId(), userId);
        if (effectiveProgress(selected) >= 100 || "completed".equals(selected.getStatus())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "已完成任务不能延后");
        }

        List<StudyTask> tasks = studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goal.getId());
        int selectedTaskOrder = orderOf(selectedParent.getOrderNum());
        int selectedSubtaskOrder = orderOf(selected.getOrderNum());
        List<StudySubtask> changedSubtasks = new ArrayList<>();
        List<StudyTask> changedTasks = new ArrayList<>();
        for (StudyTask task : tasks) {
            int taskOrder = orderOf(task.getOrderNum());
            List<StudySubtask> subtasks = subtasksOf(task);
            if (!subtasks.isEmpty()) {
                boolean taskChanged = false;
                for (StudySubtask subtask : subtasks) {
                    int subtaskOrder = orderOf(subtask.getOrderNum());
                    boolean afterSelected = taskOrder > selectedTaskOrder
                            || (task == selectedParent && subtaskOrder >= selectedSubtaskOrder);
                    if (!afterSelected || effectiveProgress(subtask) >= 100
                            || "completed".equals(subtask.getStatus())) {
                        continue;
                    }
                    shiftSubtaskDates(subtask, days, task, goal);
                    changedSubtasks.add(subtask);
                    taskChanged = true;
                }
                if (taskChanged) {
                    refreshParentSchedule(task, subtasks);
                    if (!changedTasks.contains(task)) {
                        changedTasks.add(task);
                    }
                }
            } else if (taskOrder > selectedTaskOrder && effectiveProgress(task) < 100
                    && !"completed".equals(task.getStatus())) {
                shiftTaskDates(task, days, goal);
                changedTasks.add(task);
            }
        }
        if (changedSubtasks.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "未找到可延后的细分任务");
        }
        studySubtaskRepository.saveAll(changedSubtasks);
        if (!changedTasks.isEmpty()) {
            studyTaskRepository.saveAll(changedTasks);
        }
        return getGoalDetail(goal.getId(), userId, "all");
    }

    @Override
    public StudyGoalDTO.GoalDetail getGoalDetail(Long goalId, Long userId, String filter) {
        StudyGoal goal = requireOwnedGoal(goalId, userId);
        List<StudyTask> tasks = switch (filter == null ? "all" : filter) {
            case "pending" -> studyTaskRepository.findByGoalIdAndIsCompletedFalseOrderByOrderNumAscIdAsc(goalId);
            case "completed" -> studyTaskRepository.findByGoalIdAndIsCompletedTrueOrderByOrderNumAscIdAsc(goalId);
            default -> studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goalId);
        };
        if ("today".equals(filter)) {
            LocalDate today = LocalDate.now();
            tasks = tasks.stream()
                    .filter(task -> task.getPlannedStartDate() != null
                            && task.getPlannedEndDate() != null
                            && !today.isBefore(task.getPlannedStartDate())
                            && !today.isAfter(task.getPlannedEndDate()))
                    .toList();
        }
        StudyGoalDTO.GoalDetail detail = new StudyGoalDTO.GoalDetail();
        detail.setGoal(toGoalView(goal));
        for (StudyTask task : tasks) {
            detail.getTasks().add(toTaskView(task));
        }
        return detail;
    }

    @Override
    public List<StudyGoalDTO.TaskView> getRemainingTasks(Long goalId, Long userId) {
        requireOwnedGoal(goalId, userId);
        List<StudyGoalDTO.TaskView> views = new ArrayList<>();
        for (StudyTask task : studyTaskRepository.findByGoalIdAndIsCompletedFalseOrderByOrderNumAscIdAsc(goalId)) {
            views.add(toTaskView(task));
        }
        return views;
    }

    @Override
    public PageResponse<StudyGoalDTO.GoalSummary> listMyGoals(Long userId, Integer page, Integer size) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        Page<StudyGoal> goalPage = studyGoalRepository.findByUserIdOrderByUpdatedAtDesc(
                userId, PageRequest.of(pageNo - 1, pageSize));

        List<Long> goalIds = new ArrayList<>();
        for (StudyGoal goal : goalPage.getContent()) {
            goalIds.add(goal.getId());
        }
        Map<Long, long[]> counters = new HashMap<>();
        if (!goalIds.isEmpty()) {
            List<StudyTask> tasks = studyTaskRepository.findByGoalIdInOrderByGoalIdAscOrderNumAscIdAsc(goalIds);
            Map<Long, List<StudyTask>> tasksByGoal = new HashMap<>();
            List<Long> taskIds = new ArrayList<>();
            for (StudyTask task : tasks) {
                tasksByGoal.computeIfAbsent(task.getGoalId(), ignored -> new ArrayList<>()).add(task);
                taskIds.add(task.getId());
            }
            Map<Long, List<StudySubtask>> subtasksByTask = new HashMap<>();
            if (!taskIds.isEmpty()) {
                for (StudySubtask subtask : studySubtaskRepository.findByTaskIdInOrderByTaskIdAscOrderNumAscIdAsc(taskIds)) {
                    subtasksByTask.computeIfAbsent(subtask.getTaskId(), ignored -> new ArrayList<>()).add(subtask);
                }
            }
            for (Long goalId : goalIds) {
                long total = 0;
                long completed = 0;
                for (StudyTask task : tasksByGoal.getOrDefault(goalId, List.of())) {
                    List<StudySubtask> subtasks = subtasksByTask.getOrDefault(task.getId(), List.of());
                    if (subtasks.isEmpty()) {
                        total++;
                        if (effectiveProgress(task) >= 100) {
                            completed++;
                        }
                    } else {
                        total += subtasks.size();
                        completed += subtasks.stream().filter(subtask -> effectiveProgress(subtask) >= 100).count();
                    }
                }
                counters.put(goalId, new long[]{total, completed});
            }
        }

        List<StudyGoalDTO.GoalSummary> summaries = new ArrayList<>();
        for (StudyGoal goal : goalPage.getContent()) {
            long[] count = counters.getOrDefault(goal.getId(), new long[]{0, 0});
            StudyGoalDTO.GoalSummary summary = new StudyGoalDTO.GoalSummary();
            summary.setId(goal.getId());
            summary.setTitle(goal.getTitle());
            summary.setDescription(goal.getDescription());
            summary.setProgress(goal.getProgress());
            summary.setStatus(goal.getStatus());
            summary.setStartDate(goal.getStartDate());
            summary.setTargetDate(goal.getTargetDate());
            summary.setDailyStudyMinutes(goal.getDailyStudyMinutes());
            summary.setTotalTasks((int) count[0]);
            summary.setCompletedTasks((int) count[1]);
            summary.setRemainingTasks((int) (count[0] - count[1]));
            summary.setCreatedAt(goal.getCreatedAt() == null ? null : goal.getCreatedAt().toString());
            summary.setUpdatedAt(goal.getUpdatedAt() == null ? null : goal.getUpdatedAt().toString());
            summaries.add(summary);
        }
        return new PageResponse<>(summaries, goalPage.getTotalElements(), pageNo, pageSize);
    }

    @Override
    @Transactional
    public void deleteGoal(Long goalId, Long userId) {
        StudyGoal goal = requireOwnedGoal(goalId, userId);
        List<StudyTask> tasks = studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goalId);
        if (!tasks.isEmpty()) {
            List<Long> taskIds = tasks.stream().map(StudyTask::getId).toList();
            studySubtaskRepository.deleteByTaskIdIn(taskIds);
            studyTaskRepository.deleteAllInBatch(tasks);
        }
        studyGoalRepository.delete(goal);
    }

    private StudyGoal requireOwnedGoal(Long goalId, Long userId) {
        return studyGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "目标不存在或无权访问"));
    }

    private StudySubtask requireOwnedSubtask(Long subtaskId, Long userId) {
        StudySubtask subtask = studySubtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "细分任务不存在"));
        requireOwnedSubtaskParent(subtask, userId);
        return subtask;
    }

    private StudyTask requireOwnedSubtaskParent(StudySubtask subtask, Long userId) {
        StudyTask parent = studyTaskRepository.findById(subtask.getTaskId())
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "父任务不存在"));
        requireOwnedGoal(parent.getGoalId(), userId);
        return parent;
    }

    private StudyGoalDTO.GoalView refreshGoalAfterSubtaskChange(Long taskId, Long userId) {
        StudyTask parent = studyTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "父任务不存在"));
        StudyGoal goal = requireOwnedGoal(parent.getGoalId(), userId);
        applyProgress(goal, studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goal.getId()));
        return toGoalView(goal);
    }

    private void validateTaskStatus(String status) {
        if (status == null || !List.of("pending", "in_progress", "blocked", "skipped", "completed").contains(status)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "任务状态不合法");
        }
    }

    private void validatePostponeDays(Integer days) {
        if (days == null || days < 1 || days > 30) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "延后天数必须在 1 到 30 天之间");
        }
    }

    private int orderOf(Integer orderNum) {
        return orderNum == null ? Integer.MAX_VALUE : orderNum;
    }

    private void shiftSubtaskDates(StudySubtask subtask, int days, StudyTask parent, StudyGoal goal) {
        LocalDate start = subtask.getPlannedStartDate();
        if (start == null) {
            start = parent.getPlannedStartDate();
        }
        if (start == null) {
            start = goal.getStartDate() == null ? LocalDate.now() : goal.getStartDate();
        }
        LocalDate end = subtask.getPlannedEndDate();
        if (end == null) {
            end = start.plusDays(normalizeEstimatedDays(subtask.getEstimatedDays()) - 1L);
        }
        subtask.setPlannedStartDate(start.plusDays(days));
        subtask.setPlannedEndDate(end.plusDays(days));
    }

    private void shiftTaskDates(StudyTask task, int days, StudyGoal goal) {
        LocalDate start = task.getPlannedStartDate();
        if (start == null) {
            start = goal.getStartDate() == null ? LocalDate.now() : goal.getStartDate();
        }
        LocalDate end = task.getPlannedEndDate();
        if (end == null) {
            end = start.plusDays(normalizeEstimatedDays(task.getEstimatedDays()) - 1L);
        }
        task.setPlannedStartDate(start.plusDays(days));
        task.setPlannedEndDate(end.plusDays(days));
    }

    private void refreshParentSchedule(StudyTask parent, List<StudySubtask> subtasks) {
        LocalDate start = subtasks.stream()
                .map(StudySubtask::getPlannedStartDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(parent.getPlannedStartDate());
        LocalDate end = subtasks.stream()
                .map(StudySubtask::getPlannedEndDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(parent.getPlannedEndDate());
        if (start != null) {
            parent.setPlannedStartDate(start);
        }
        if (end != null) {
            parent.setPlannedEndDate(end);
        }
        parent.setEstimatedDays(subtasks.stream()
                .mapToInt(subtask -> normalizeEstimatedDays(subtask.getEstimatedDays()))
                .sum());
    }

    /** 根据预计学习天数加权重算目标进度，避免一个大任务与一个小任务权重相同。 */
    private void applyProgress(StudyGoal goal, List<StudyTask> tasks) {
        int totalWorkItems = 0;
        int totalDays = 0;
        int weightedProgress = 0;
        boolean allCompleted = true;
        boolean hasStarted = false;
        for (StudyTask task : tasks) {
            List<StudySubtask> subtasks = subtasksOf(task);
            if (subtasks.isEmpty()) {
                int days = normalizeEstimatedDays(task.getEstimatedDays());
                int progress = effectiveProgress(task);
                totalWorkItems++;
                totalDays += days;
                weightedProgress += days * progress;
                allCompleted &= progress >= 100;
                hasStarted |= progress > 0 || isNonPending(task.getStatus());
                continue;
            }
            int taskDays = subtasks.stream()
                    .mapToInt(subtask -> normalizeEstimatedDays(subtask.getEstimatedDays()))
                    .sum();
            int taskWeightedProgress = subtasks.stream()
                    .mapToInt(subtask -> normalizeEstimatedDays(subtask.getEstimatedDays()) * effectiveProgress(subtask))
                    .sum();
            int taskProgress = taskDays == 0 ? 0 : (int) Math.round(taskWeightedProgress / (double) taskDays);
            task.setEstimatedDays(taskDays);
            task.setProgressPercent(taskProgress);
            task.setIsCompleted(taskProgress >= 100);
            task.setStatus(subtasks.stream().allMatch(subtask -> effectiveProgress(subtask) >= 100)
                    ? "completed" : taskProgress > 0 || subtasks.stream().anyMatch(subtask -> isNonPending(subtask.getStatus()))
                    ? "in_progress" : "pending");
            totalWorkItems += subtasks.size();
            totalDays += taskDays;
            weightedProgress += taskWeightedProgress;
            allCompleted &= subtasks.stream().allMatch(subtask -> effectiveProgress(subtask) >= 100);
            hasStarted |= taskProgress > 0 || subtasks.stream().anyMatch(subtask -> isNonPending(subtask.getStatus()));
        }
        int progress = totalDays == 0 ? 0 : (int) Math.round(weightedProgress / (double) totalDays);
        goal.setProgress(progress);
        goal.setStatus(totalWorkItems > 0 && allCompleted ? "completed" : hasStarted ? "in_progress" : "pending");
        studyGoalRepository.save(goal);
    }

    private StudyTask buildTaskEntity(Long goalId, StudyGoalDTO.TaskInput input, int orderNum) {
        StudyTask task = new StudyTask();
        task.setGoalId(goalId);
        task.setTaskName(trimToLength(StringUtils.trimWhitespace(input.getTaskName()), 120));
        task.setStage(trimToLength(StringUtils.trimWhitespace(input.getStage()), 60));
        task.setEstimatedDays(normalizeEstimatedDays(input.getEstimatedDays()));
        task.setPriority(isAllowedPriority(input.getPriority()) ? input.getPriority() : DEFAULT_PRIORITY);
        task.setOrderNum(orderNum);
        int progress = normalizeProgress(input.getProgressPercent());
        boolean completed = Boolean.TRUE.equals(input.getIsCompleted()) || progress >= 100;
        task.setProgressPercent(completed ? 100 : progress);
        task.setIsCompleted(completed);
        task.setStatus(completed ? "completed" : progress > 0 ? "in_progress" : "pending");
        task.setDescription(StringUtils.trimWhitespace(input.getDescription()));
        return task;
    }

    private StudySubtask buildSubtaskEntity(Long taskId, StudyGoalDTO.SubtaskInput input) {
        StudySubtask subtask = new StudySubtask();
        subtask.setTaskId(taskId);
        subtask.setTaskName(trimToLength(StringUtils.trimWhitespace(input.getTaskName()), 120));
        subtask.setDescription(StringUtils.trimWhitespace(input.getDescription()));
        subtask.setEstimatedDays(normalizeEstimatedDays(input.getEstimatedDays()));
        int progress = normalizeProgress(input.getProgressPercent());
        boolean completed = Boolean.TRUE.equals(input.getIsCompleted()) || progress >= 100;
        subtask.setProgressPercent(completed ? 100 : progress);
        subtask.setIsCompleted(completed);
        subtask.setStatus(completed ? "completed" : progress > 0 ? "in_progress" : "pending");
        return subtask;
    }

    private void applyProgressToSubtasks(List<StudySubtask> subtasks, int progress) {
        boolean completed = progress >= 100;
        String status = completed ? "completed" : progress > 0 ? "in_progress" : "pending";
        for (StudySubtask subtask : subtasks) {
            subtask.setProgressPercent(progress);
            subtask.setIsCompleted(completed);
            subtask.setStatus(status);
        }
    }

    private List<StudyGoalDTO.SubtaskInput> validSubtaskInputs(List<StudyGoalDTO.SubtaskInput> inputs) {
        List<StudyGoalDTO.SubtaskInput> valid = new ArrayList<>();
        if (inputs == null) {
            return valid;
        }
        for (StudyGoalDTO.SubtaskInput input : inputs) {
            if (input != null && StringUtils.hasText(input.getTaskName())) {
                valid.add(input);
            }
            if (valid.size() >= 6) {
                break;
            }
        }
        return valid;
    }

    private int sumEstimatedDays(List<StudyGoalDTO.SubtaskInput> inputs) {
        return inputs.stream().mapToInt(input -> normalizeEstimatedDays(input.getEstimatedDays())).sum();
    }

    private void scheduleSubtasks(List<StudySubtask> subtasks, LocalDate startDate) {
        LocalDate cursor = startDate;
        for (int index = 0; index < subtasks.size(); index++) {
            StudySubtask subtask = subtasks.get(index);
            int days = normalizeEstimatedDays(subtask.getEstimatedDays());
            subtask.setPlannedStartDate(cursor);
            subtask.setPlannedEndDate(cursor.plusDays(days - 1L));
            subtask.setOrderNum(index + 1);
            cursor = cursor.plusDays(days);
        }
    }

    private void scheduleTasks(List<StudyTask> tasks, LocalDate startDate) {
        LocalDate cursor = startDate;
        for (StudyTask task : tasks) {
            int days = normalizeEstimatedDays(task.getEstimatedDays());
            task.setPlannedStartDate(cursor);
            task.setPlannedEndDate(cursor.plusDays(days - 1L));
            cursor = cursor.plusDays(days);
        }
    }

    private Integer normalizeEstimatedDays(Integer estimatedDays) {
        if (estimatedDays == null || estimatedDays < 1) {
            return 1;
        }
        return Math.min(estimatedDays, MAX_ESTIMATED_DAYS);
    }

    private Integer normalizeDailyStudyMinutes(Integer dailyStudyMinutes) {
        if (dailyStudyMinutes == null) {
            return 60;
        }
        if (dailyStudyMinutes < 15 || dailyStudyMinutes > 720) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "每日学习时长必须在 15 到 720 分钟之间");
        }
        return dailyStudyMinutes;
    }

    private int normalizeProgress(Integer progressPercent) {
        if (progressPercent == null) {
            return 0;
        }
        if (progressPercent < 0 || progressPercent > 100) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "任务进度必须在 0 到 100 之间");
        }
        return progressPercent;
    }

    private int effectiveProgress(StudyTask task) {
        if (task.getProgressPercent() != null) {
            return Math.max(0, Math.min(100, task.getProgressPercent()));
        }
        return Boolean.TRUE.equals(task.getIsCompleted()) ? 100 : 0;
    }

    private int effectiveProgress(StudySubtask subtask) {
        if (subtask.getProgressPercent() != null) {
            return Math.max(0, Math.min(100, subtask.getProgressPercent()));
        }
        return Boolean.TRUE.equals(subtask.getIsCompleted()) ? 100 : 0;
    }

    private boolean isNonPending(String status) {
        return status != null && !"pending".equals(status);
    }

    private List<StudySubtask> subtasksOf(StudyTask task) {
        return task.getId() == null ? List.of()
                : studySubtaskRepository.findByTaskIdOrderByOrderNumAscIdAsc(task.getId());
    }

    @SuppressWarnings("unchecked")
    private StudyGoalDTO.DecomposeResponse toDecomposeResponse(Object rawResult) {
        StudyGoalDTO.AgentDecomposeResult agentResult;
        try {
            agentResult = objectMapper.convertValue(rawResult, StudyGoalDTO.AgentDecomposeResult.class);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(Result.ERROR_CODE, "智能体拆解结果解析失败");
        }
        if (agentResult == null || !StringUtils.hasText(agentResult.getGoal() == null ? null : agentResult.getGoal().getTitle())) {
            throw new BusinessException(Result.ERROR_CODE, "智能体未返回有效的目标信息");
        }

        StudyGoalDTO.DecomposeResponse response = new StudyGoalDTO.DecomposeResponse();
        StudyGoalDTO.GoalView goalView = new StudyGoalDTO.GoalView();
        goalView.setTitle(StringUtils.trimWhitespace(agentResult.getGoal().getTitle()));
        goalView.setDescription(StringUtils.trimWhitespace(agentResult.getGoal().getDescription()));
        goalView.setProgress(0);
        goalView.setStatus("pending");
        response.setGoal(goalView);

        int orderNum = 1;
        if (agentResult.getTasks() != null) {
            for (StudyGoalDTO.AgentTask agentTask : agentResult.getTasks()) {
                if (agentTask == null || !StringUtils.hasText(agentTask.getTaskName())) {
                    continue;
                }
                StudyGoalDTO.TaskView view = new StudyGoalDTO.TaskView();
                view.setTaskName(StringUtils.trimWhitespace(agentTask.getTaskName()));
                view.setStage(StringUtils.trimWhitespace(agentTask.getStage()));
                Integer days = agentTask.getEstimatedDays();
                view.setEstimatedDays(days == null ? 1 : Math.max(1, Math.min(days, MAX_ESTIMATED_DAYS)));
                view.setPriority(isAllowedPriority(agentTask.getPriority())
                        ? agentTask.getPriority() : DEFAULT_PRIORITY);
                view.setOrderNum(orderNum++);
                view.setStatus("pending");
                view.setIsCompleted(false);
                view.setProgressPercent(0);
                view.setDescription(StringUtils.trimWhitespace(agentTask.getDescription()));
                int subtaskOrder = 1;
                for (StudyGoalDTO.AgentSubtask agentSubtask : agentTask.getSubtasks() == null
                        ? List.<StudyGoalDTO.AgentSubtask>of() : agentTask.getSubtasks()) {
                    if (agentSubtask == null || !StringUtils.hasText(agentSubtask.getTaskName())) {
                        continue;
                    }
                    StudyGoalDTO.SubtaskView subtaskView = new StudyGoalDTO.SubtaskView();
                    subtaskView.setTaskName(StringUtils.trimWhitespace(agentSubtask.getTaskName()));
                    subtaskView.setDescription(StringUtils.trimWhitespace(agentSubtask.getDescription()));
                    subtaskView.setEstimatedDays(normalizeEstimatedDays(agentSubtask.getEstimatedDays()));
                    subtaskView.setOrderNum(subtaskOrder++);
                    subtaskView.setProgressPercent(0);
                    subtaskView.setIsCompleted(false);
                    subtaskView.setStatus("pending");
                    view.getSubtasks().add(subtaskView);
                }
                if (!view.getSubtasks().isEmpty()) {
                    view.setEstimatedDays(view.getSubtasks().stream()
                            .mapToInt(subtask -> normalizeEstimatedDays(subtask.getEstimatedDays())).sum());
                }
                response.getTasks().add(view);
            }
        }
        if (response.getTasks().isEmpty()) {
            throw new BusinessException(Result.ERROR_CODE, "智能体未返回有效任务");
        }
        return response;
    }

    private StudyGoalDTO.GoalView toGoalView(StudyGoal goal) {
        StudyGoalDTO.GoalView view = new StudyGoalDTO.GoalView();
        view.setId(goal.getId());
        view.setTitle(goal.getTitle());
        view.setDescription(goal.getDescription());
        view.setStartDate(goal.getStartDate());
        view.setTargetDate(goal.getTargetDate());
        view.setDailyStudyMinutes(goal.getDailyStudyMinutes());
        view.setProgress(goal.getProgress());
        view.setStatus(goal.getStatus());
        return view;
    }

    private StudyGoalDTO.TaskView toTaskView(StudyTask task) {
        StudyGoalDTO.TaskView view = new StudyGoalDTO.TaskView();
        view.setId(task.getId());
        view.setGoalId(task.getGoalId());
        view.setTaskName(task.getTaskName());
        view.setStage(task.getStage());
        view.setEstimatedDays(task.getEstimatedDays());
        view.setPlannedStartDate(task.getPlannedStartDate());
        view.setPlannedEndDate(task.getPlannedEndDate());
        view.setPriority(task.getPriority());
        view.setOrderNum(task.getOrderNum());
        view.setStatus(task.getStatus());
        view.setIsCompleted(Boolean.TRUE.equals(task.getIsCompleted()));
        view.setProgressPercent(effectiveProgress(task));
        view.setDescription(task.getDescription());
        for (StudySubtask subtask : subtasksOf(task)) {
            view.getSubtasks().add(toSubtaskView(subtask));
        }
        return view;
    }

    private StudyGoalDTO.SubtaskView toSubtaskView(StudySubtask subtask) {
        StudyGoalDTO.SubtaskView view = new StudyGoalDTO.SubtaskView();
        view.setId(subtask.getId());
        view.setTaskId(subtask.getTaskId());
        view.setTaskName(subtask.getTaskName());
        view.setDescription(subtask.getDescription());
        view.setEstimatedDays(subtask.getEstimatedDays());
        view.setPlannedStartDate(subtask.getPlannedStartDate());
        view.setPlannedEndDate(subtask.getPlannedEndDate());
        view.setProgressPercent(effectiveProgress(subtask));
        view.setStatus(subtask.getStatus());
        view.setIsCompleted(Boolean.TRUE.equals(subtask.getIsCompleted()));
        view.setOrderNum(subtask.getOrderNum());
        return view;
    }

    private boolean isAllowedPriority(String priority) {
        return priority != null && ALLOWED_PRIORITIES.contains(priority);
    }

    private String resolveTableSuffix(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return "xlsx";
        }
        if (lower.endsWith(".csv")) {
            return "csv";
        }
        throw new BusinessException(Result.BAD_REQUEST_CODE, "仅支持 .xlsx 或 .csv 数据表文件");
    }

    /** 把首个工作表序列化为「列以 | 分隔」的表格文本。 */
    private String extractXlsxText(MultipartFile file) {
        try (InputStream in = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = workbook.getSheetAt(0);
            StringBuilder text = new StringBuilder();
            int rowCount = 0;
            for (Row row : sheet) {
                if (rowCount >= MAX_TABLE_ROWS) {
                    break;
                }
                List<String> cells = new ArrayList<>();
                int lastCol = Math.min(row.getLastCellNum(), MAX_TABLE_COLS);
                for (int col = 0; col < lastCol; col++) {
                    Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                appendNonEmptyRow(text, cells);
                rowCount++;
            }
            return text.toString().trim();
        } catch (IOException | RuntimeException e) {
            log.warn("parse xlsx failed: {}", e.getMessage());
            throw new BusinessException(Result.BAD_REQUEST_CODE, "Excel 文件解析失败，请确认是有效的 .xlsx 文件");
        }
    }

    private String extractCsvText(MultipartFile file) {
        try {
            String csvText = readCsvText(file);
            StringBuilder text = new StringBuilder();
            int rowCount = 0;
            for (List<String> row : parseCsvRows(csvText)) {
                if (rowCount >= MAX_TABLE_ROWS) {
                    break;
                }
                appendNonEmptyRow(text, row);
                rowCount++;
            }
            return text.toString().trim();
        } catch (IOException e) {
            log.warn("read csv failed: {}", e.getMessage());
            throw new BusinessException(Result.BAD_REQUEST_CODE, "CSV 文件读取失败");
        }
    }

    private void appendNonEmptyRow(StringBuilder text, List<String> cells) {
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        if (cells.isEmpty()) {
            return;
        }
        text.append(String.join(CELL_DELIMITER, cells)).append('\n');
    }

    /** UTF-8 解码失败时回退 GBK（常见中文 CSV 编码）。 */
    private String readCsvText(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            decoded = new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
        if (decoded.startsWith("\uFEFF")) {
            decoded = decoded.substring(1);
        }
        return decoded;
    }

    /** 简易 CSV 行切分：支持双引号包裹、转义引号；忽略空行。 */
    private List<List<String>> parseCsvRows(String csvText) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean hasCell = false;
        String text = csvText == null ? "" : csvText;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(current);
                }
            } else if (current == '"') {
                inQuotes = true;
                hasCell = true;
            } else if (current == ',') {
                currentRow.add(cell.toString());
                cell.setLength(0);
                hasCell = false;
            } else if (current == '\r') {
                // 忽略回车，换行统一由 \n 处理
            } else if (current == '\n') {
                currentRow.add(cell.toString());
                cell.setLength(0);
                hasCell = false;
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                if (rows.size() >= MAX_TABLE_ROWS * 2) {
                    break;
                }
            } else {
                cell.append(current);
                hasCell = true;
            }
        }
        if (cell.length() > 0 || hasCell || !currentRow.isEmpty()) {
            currentRow.add(cell.toString());
            rows.add(currentRow);
        }
        return rows;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
