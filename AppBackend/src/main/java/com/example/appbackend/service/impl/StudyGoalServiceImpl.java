package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.StudyGoalDTO;
import com.example.appbackend.entity.StudyGoal;
import com.example.appbackend.entity.StudyTask;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.entity.Result;
import com.example.appbackend.repository.StudyGoalRepository;
import com.example.appbackend.repository.StudyTaskRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习计划结构化拆解服务实现。
 *
 * 拆解链路：xlsx/csv/text -> 表格序列化文本 -> ai-servers 专用智能体 -> 纯 JSON 预览。
 * Goal 进度由已完成任务数量自动计算，任务勾选与进度重算共用 applyProgress。
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
    private final ObjectMapper objectMapper;

    public StudyGoalServiceImpl(PythonAiProxyService pythonAiProxyService,
                                StudyGoalRepository studyGoalRepository,
                                StudyTaskRepository studyTaskRepository,
                                ObjectMapper objectMapper) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.studyGoalRepository = studyGoalRepository;
        this.studyTaskRepository = studyTaskRepository;
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
            log.info("study goal decompose content truncated, userId={}, originLength={}", userId, content.length());
            content = content.substring(0, MAX_CONTENT_CHARS);
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
        goal.setProgress(0);
        goal.setStatus("pending");
        goal = studyGoalRepository.save(goal);

        List<StudyTask> tasks = new ArrayList<>();
        int orderNum = 1;
        for (StudyGoalDTO.TaskInput input : inputs) {
            tasks.add(buildTaskEntity(goal.getId(), input, orderNum++));
        }
        tasks = studyTaskRepository.saveAll(tasks);
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
        StudyTask task = studyTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "任务不存在"));
        requireOwnedGoal(task.getGoalId(), userId);
        boolean completed = Boolean.TRUE.equals(isCompleted);
        task.setIsCompleted(completed);
        task.setStatus(completed ? "completed" : "pending");
        studyTaskRepository.save(task);

        StudyGoal goal = requireOwnedGoal(task.getGoalId(), userId);
        applyProgress(goal, studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goal.getId()));
        return toGoalView(goal);
    }

    @Override
    public StudyGoalDTO.GoalDetail getGoalDetail(Long goalId, Long userId, String filter) {
        StudyGoal goal = requireOwnedGoal(goalId, userId);
        List<StudyTask> tasks = switch (filter == null ? "all" : filter) {
            case "pending" -> studyTaskRepository.findByGoalIdAndIsCompletedFalseOrderByOrderNumAscIdAsc(goalId);
            case "completed" -> studyTaskRepository.findByGoalIdAndIsCompletedTrueOrderByOrderNumAscIdAsc(goalId);
            default -> studyTaskRepository.findByGoalIdOrderByOrderNumAscIdAsc(goalId);
        };
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
            for (StudyTaskRepository.TaskCountView view : studyTaskRepository.countByGoalIds(goalIds)) {
                counters.put(view.getGoalId(), new long[]{view.getTotal(), view.getCompleted()});
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
            summary.setTotalTasks((int) count[0]);
            summary.setCompletedTasks((int) count[1]);
            summary.setRemainingTasks((int) (count[0] - count[1]));
            summary.setCreatedAt(goal.getCreatedAt() == null ? null : goal.getCreatedAt().toString());
            summary.setUpdatedAt(goal.getUpdatedAt() == null ? null : goal.getUpdatedAt().toString());
            summaries.add(summary);
        }
        return new PageResponse<>(summaries, goalPage.getTotalElements(), pageNo, pageSize);
    }

    private StudyGoal requireOwnedGoal(Long goalId, Long userId) {
        return studyGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "目标不存在或无权访问"));
    }

    /**
     * 根据已完成任务数量重算目标进度与状态：0 条完成 -> pending，全部完成 -> completed，其余 in_progress。
     */
    private void applyProgress(StudyGoal goal, List<StudyTask> tasks) {
        long completed = tasks.stream().filter(task -> Boolean.TRUE.equals(task.getIsCompleted())).count();
        int total = tasks.size();
        int progress = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        goal.setProgress(progress);
        goal.setStatus(completed == 0 ? "pending" : completed >= total ? "completed" : "in_progress");
        studyGoalRepository.save(goal);
    }

    private StudyTask buildTaskEntity(Long goalId, StudyGoalDTO.TaskInput input, int orderNum) {
        StudyTask task = new StudyTask();
        task.setGoalId(goalId);
        task.setTaskName(trimToLength(StringUtils.trimWhitespace(input.getTaskName()), 120));
        task.setStage(trimToLength(StringUtils.trimWhitespace(input.getStage()), 60));
        task.setEstimatedDays(normalizeEstimatedDays(input.getEstimatedDays()));
        task.setPriority(ALLOWED_PRIORITIES.contains(input.getPriority()) ? input.getPriority() : DEFAULT_PRIORITY);
        task.setOrderNum(orderNum);
        boolean completed = Boolean.TRUE.equals(input.getIsCompleted());
        task.setIsCompleted(completed);
        task.setStatus(completed ? "completed" : "pending");
        task.setDescription(StringUtils.trimWhitespace(input.getDescription()));
        return task;
    }

    private Integer normalizeEstimatedDays(Integer estimatedDays) {
        if (estimatedDays == null || estimatedDays < 1) {
            return 1;
        }
        return Math.min(estimatedDays, MAX_ESTIMATED_DAYS);
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
                view.setPriority(ALLOWED_PRIORITIES.contains(agentTask.getPriority())
                        ? agentTask.getPriority() : DEFAULT_PRIORITY);
                view.setOrderNum(orderNum++);
                view.setStatus("pending");
                view.setIsCompleted(false);
                view.setDescription(StringUtils.trimWhitespace(agentTask.getDescription()));
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
        view.setPriority(task.getPriority());
        view.setOrderNum(task.getOrderNum());
        view.setStatus(task.getStatus());
        view.setIsCompleted(Boolean.TRUE.equals(task.getIsCompleted()));
        view.setDescription(task.getDescription());
        return view;
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
