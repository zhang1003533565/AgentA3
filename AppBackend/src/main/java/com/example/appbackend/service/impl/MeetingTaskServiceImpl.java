package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AgentConfirmTaskRequest;
import com.example.appbackend.dto.CreateTaskRequest;
import com.example.appbackend.dto.TaskDetailVO;
import com.example.appbackend.dto.UpdateTaskStatusRequest;
import com.example.appbackend.entity.MeetingTask;
import com.example.appbackend.entity.MeetingParticipant;
import com.example.appbackend.entity.TaskStatus;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MeetingParticipantRepository;
import com.example.appbackend.repository.MeetingTaskRepository;
import com.example.appbackend.service.MeetingTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会议任务服务实现
 */
@Service
public class MeetingTaskServiceImpl implements MeetingTaskService {

    /**
     * 从确认完成的 evidence 中提取 [说话人：XXX] 标记，用于服务端复核发言人身份
     */
    private static final java.util.regex.Pattern SPEAKER_PATTERN =
            java.util.regex.Pattern.compile("\\[说话人[：:]\\s*([^\\]]+?)\\s*\\]");

    /**
     * 提取任务标题中的核心词：连续中文段或英文单词，用于 evidence 关联性校验
     */
    private static final java.util.regex.Pattern TITLE_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}|[A-Za-z]{2,}");

    /**
     * evidence 与任务标题的二元组覆盖率阈值：达到该比例视为原句确实在讲这个任务。
     * 取 0.4 以容纳负责人自然语序的复述（如"部署文档我写完了"对应标题"编写新的部署文档"）。
     */
    private static final double TITLE_BIGRAM_THRESHOLD = 0.4;

    private final MeetingTaskRepository taskRepository;
    private final MeetingParticipantRepository participantRepository;

    public MeetingTaskServiceImpl(MeetingTaskRepository taskRepository,
                                  MeetingParticipantRepository participantRepository) {
        this.taskRepository = taskRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    @Transactional
    public TaskDetailVO createTask(CreateTaskRequest request) {
        // 幂等检查：同一会议 + 同一负责人 + 相同标题时不重复创建
        boolean exists = taskRepository.existsByMeetingSessionIdAndAssigneeIdAndTitle(
                request.getMeetingSessionId(),
                request.getAssigneeId(),
                request.getTitle()
        );
        
        if (exists) {
            // 返回已存在的任务，而不是重新创建
            List<MeetingTask> existingTasks = taskRepository.findByMeetingSessionIdAndAssigneeIdAndTitle(
                    request.getMeetingSessionId(),
                    request.getAssigneeId(),
                    request.getTitle()
            );
            
            if (!existingTasks.isEmpty()) {
                return convertToVO(existingTasks.get(0));
            }
        }

        // 创建新任务
        MeetingTask task = new MeetingTask();
        task.setMeetingSessionId(request.getMeetingSessionId());
        task.setAssigneeId(request.getAssigneeId());
        task.setAssigneeName(request.getAssigneeName());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(TaskStatus.PENDING);
        task.setEvidence(request.getEvidence());

        // 设置截止时间（如果提供）
        if (request.getDeadline() != null && !request.getDeadline().isEmpty()) {
            try {
                task.setDeadline(LocalDateTime.parse(request.getDeadline()));
            } catch (Exception e) {
                // 时间格式错误，设置为 null
                task.setDeadline(null);
            }
        }

        task = taskRepository.save(task);
        return convertToVO(task);
    }

    @Override
    public List<TaskDetailVO> listMyTasks(Long userId, Long meetingSessionId, String statusStr) {
        TaskStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = TaskStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("无效的任务状态：" + statusStr);
            }
        }

        List<MeetingTask> tasks;
        
        if (meetingSessionId != null) {
            // 查询指定会议的任务
            if (status != null) {
                tasks = taskRepository.findByAssigneeIdAndStatusAndMeetingSessionId(userId, status, meetingSessionId);
            } else {
                tasks = taskRepository.findByAssigneeIdAndMeetingSessionId(userId, meetingSessionId);
            }
        } else if (status != null) {
            // 查询指定状态的所有任务
            tasks = taskRepository.findByAssigneeIdAndStatus(userId, status);
        } else {
            // 查询所有任务
            tasks = taskRepository.findByAssigneeId(userId);
        }

        return tasks.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public TaskDetailVO getTaskById(Long taskId, Long currentUserId) {
        MeetingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("未找到该任务"));

        // 权限校验：只有任务负责人或管理员可以查看
        // 注意：这里假设 currentUserId 为 null 表示未登录，应该拒绝访问
        if (currentUserId == null || !currentUserId.equals(task.getAssigneeId())) {
            throw new BusinessException("无权限查看该任务");
        }

        return convertToVO(task);
    }

    @Override
    @Transactional
    public TaskDetailVO updateTaskStatus(Long taskId, Long currentUserId, UpdateTaskStatusRequest request) {
        MeetingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("未找到该任务"));

        // 【核心权限控制】只有任务负责人本人才能完成任务
        if (!currentUserId.equals(task.getAssigneeId())) {
            throw new BusinessException("无权限完成任务：只有任务负责人" + task.getAssigneeName() + "才能更新该任务状态");
        }

        // 解析状态
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的任务状态：" + request.getStatus());
        }

        // 只能从 PENDING 改为 COMPLETED
        if (newStatus == TaskStatus.COMPLETED && task.getStatus() != TaskStatus.COMPLETED) {
            // 标记为完成
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletedBy(currentUserId);
        } else if (newStatus == TaskStatus.PENDING) {
            // 允许将 COMPLETED 改回 PENDING（重新激活）
            task.setStatus(TaskStatus.PENDING);
            task.setCompletedAt(null);
            task.setCompletedBy(null);
        } else {
            // 同状态不变，直接返回
            return convertToVO(task);
        }

        task = taskRepository.save(task);
        return convertToVO(task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDetailVO> listPendingTasksByAssigneeIds(Collection<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return Collections.emptyList();
        }
        return taskRepository.findByStatusAndAssigneeIdIn(TaskStatus.PENDING, assigneeIds).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TaskDetailVO agentConfirmTaskCompletion(Long taskId, AgentConfirmTaskRequest request) {
        if (request == null || request.getAssigneeId() == null || request.getMeetingSessionId() == null) {
            throw new BusinessException("缺少 assigneeId 或 meetingSessionId");
        }
        MeetingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("未找到该任务"));

        // 幂等：已完成的任务不重复更新
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return convertToVO(task);
        }

        // 权限二次保护：声称的负责人必须与任务真实负责人一致，不信任 AI 传入的其他身份
        if (!Objects.equals(request.getAssigneeId(), task.getAssigneeId())) {
            throw new BusinessException("确认失败：只有任务负责人本人确认完成才有效（assigneeId 与任务负责人不一致）");
        }

        // 身份校验：该负责人必须是当前会议的真实参会人（把说话人身份锚定到本会议）
        boolean isParticipant = participantRepository
                .findByMeetingSessionIdOrderBySortOrderAscIdAsc(request.getMeetingSessionId())
                .stream()
                .anyMatch(participant -> Objects.equals(participant.getUserId(), request.getAssigneeId()));
        if (!isParticipant) {
            throw new BusinessException("确认失败：该负责人不是本次会议的参会人，无法确认其发言身份");
        }

        // 说话人强校验（第六步真实测试发现的缺口）：evidence 中 [说话人：XXX] 必须是任务负责人本人。
        // AI 判断"谁在说话"不可信，必须由服务端复核，防止非负责人代报完成。
        String evidence = request.getEvidence() == null ? "" : request.getEvidence().trim();
        java.util.regex.Matcher speakerMatcher = SPEAKER_PATTERN.matcher(evidence);
        if (!speakerMatcher.find()) {
            // evidence 必须是带说话人标记的会议原句；缺失说明 AI 未正确引用原文，拒绝确认
            throw new BusinessException("确认失败：evidence 缺少 [说话人：XXX] 标记，无法核验发言人身份");
        }
        String speakerName = speakerMatcher.group(1).trim();
        String assigneeName = task.getAssigneeName() == null ? "" : task.getAssigneeName().trim();
        if (!speakerName.equals(assigneeName)) {
            throw new BusinessException("确认失败：发言人是「" + speakerName + "」，不是任务负责人本人「" + assigneeName + "」，不能代报完成");
        }

        // 完成语义校验（第六步真实测试发现的缺口）："已经开始整理"含标题词且说话人正确，
        // 但语义是"进行中"不是"完成"。evidence 原句含未完成标记词时拒绝确认。
        String incompleteMarker = findIncompleteMarker(evidence);
        if (incompleteMarker != null) {
            throw new BusinessException("确认失败：evidence 原句含进行中表述「" + incompleteMarker + "」，不满足负责人本人明确确认完成，任务保持待完成");
        }

        // 关联性校验（第六步真实测试发现的缺口）："之前那个事情处理好了"这类模糊原句无法唯一对应任务。
        // 要求 evidence 与任务标题有足够的字符重叠，防止 AI 把无法唯一匹配的原句强行关联到某任务。
        if (!evidenceContainsTaskTitle(evidence, task.getTitle())) {
            throw new BusinessException("确认失败：evidence 原句与任务「" + task.getTitle() + "」无明确关联，不能确认完成");
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        // completedBy 记录任务负责人本人，而不是调用方（主持人/AI）的 userId
        task.setCompletedBy(task.getAssigneeId());
        task = taskRepository.save(task);
        return convertToVO(task);
    }

    /**
     * 未完成/进行中标记词：原句含这些词时语义不是"明确完成"，拒绝确认。
     */
    private static final java.util.List<String> INCOMPLETE_MARKERS = java.util.List.of(
            "开始", "着手", "进行中", "正在", "一部分", "部分完成", "差不多了", "差不多",
            "基本完成", "快完成", "接近完成", "尽快", "准备", "计划", "打算",
            "还差", "尚未", "还没", "继续", "整理中", "处理中", "写了一半", "做了一半"
    );

    /**
     * 检测 evidence 原句是否含未完成/进行中标记词，命中返回该词，否则返回 null。
     */
    private String findIncompleteMarker(String evidence) {
        for (String marker : INCOMPLETE_MARKERS) {
            if (evidence.contains(marker)) {
                return marker;
            }
        }
        return null;
    }

    /**
     * 校验 evidence 原句与任务标题的关联性。
     * 采用字符二元组（bigram）覆盖率而非整词包含：负责人常以自然语序复述任务
     * （标题"编写新的部署文档" → 原句"新的部署文档我已经编写完成了"），
     * 整词包含会误拒；而"之前那个事情已经处理好了"这类无关原句覆盖率为 0，仍能拦住。
     */
    private boolean evidenceContainsTaskTitle(String evidence, String title) {
        if (title == null || title.trim().isEmpty()) {
            return true;
        }
        java.util.List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = TITLE_TOKEN_PATTERN.matcher(title);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return true;
        }
        for (String token : tokens) {
            if (evidence.contains(token)) {
                return true;
            }
            if (bigramCoverage(evidence, token) >= TITLE_BIGRAM_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算 token 的字符二元组在 evidence 中出现的比例（0.0 ~ 1.0）。
     */
    private double bigramCoverage(String evidence, String token) {
        int total = 0;
        int hit = 0;
        for (int i = 0; i + 1 < token.length(); i++) {
            total++;
            if (evidence.contains(token.substring(i, i + 2))) {
                hit++;
            }
        }
        return total == 0 ? 0.0 : (double) hit / total;
    }

    /**
     * 转换实体为 VO
     */
    private TaskDetailVO convertToVO(MeetingTask task) {
        TaskDetailVO vo = new TaskDetailVO();
        vo.setId(task.getId());
        vo.setMeetingSessionId(task.getMeetingSessionId());
        vo.setAssigneeId(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setEvidence(task.getEvidence());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCompletedBy(task.getCompletedBy());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }
}
