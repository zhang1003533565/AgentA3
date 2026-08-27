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

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        // completedBy 记录任务负责人本人，而不是调用方（主持人/AI）的 userId
        task.setCompletedBy(task.getAssigneeId());
        task = taskRepository.save(task);
        return convertToVO(task);
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
