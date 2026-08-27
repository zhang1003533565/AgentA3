package com.example.appbackend.service;

import com.example.appbackend.dto.CreateTaskRequest;
import com.example.appbackend.dto.TaskDetailVO;
import com.example.appbackend.dto.UpdateTaskStatusRequest;
import com.example.appbackend.entity.MeetingTask;

import java.util.List;

/**
 * 会议任务服务接口
 */
public interface MeetingTaskService {

    /**
     * 创建个人任务（幂等处理：同一会议、同一负责人、相同标题时不会重复创建）
     * 
     * @param request 创建请求
     * @return 创建的任务详情
     */
    TaskDetailVO createTask(CreateTaskRequest request);

    /**
     * 查询当前用户的个人任务列表（只能查自己的）
     * 
     * @param userId 当前登录用户 ID
     * @param meetingSessionId 会议 ID（可选，用于筛选特定会议）
     * @param status 状态（可选）
     * @return 任务列表
     */
    List<TaskDetailVO> listMyTasks(Long userId, Long meetingSessionId, String status);

    /**
     * 根据 ID 查询任务详情（权限校验：只有任务负责人或管理员可以查看）
     * 
     * @param taskId 任务 ID
     * @param currentUserId 当前登录用户 ID
     * @return 任务详情
     */
    TaskDetailVO getTaskById(Long taskId, Long currentUserId);

    /**
     * 更新任务状态（权限校验：只有任务负责人本人才能完成任务）
     * 
     * @param taskId 任务 ID
     * @param currentUserId 当前登录用户 ID
     * @param request 状态更新请求
     * @return 更新后的任务详情
     */
    TaskDetailVO updateTaskStatus(Long taskId, Long currentUserId, UpdateTaskStatusRequest request);
}
