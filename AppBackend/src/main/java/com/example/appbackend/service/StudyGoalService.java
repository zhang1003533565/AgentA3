package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.StudyGoalDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 学习计划结构化拆解服务接口。
 */
public interface StudyGoalService {

    /**
     * 上传学习计划数据表或文本，调用专用智能体拆解为目标与任务（仅预览，不落库）。
     *
     * @param userId        当前用户ID
     * @param file          Excel(.xlsx)/CSV 数据表，与 planText 二选一
     * @param planText      粘贴的学习计划文本，与 file 二选一
     * @param authorization 用户 JWT，透传给 ai-servers
     */
    StudyGoalDTO.DecomposeResponse decompose(Long userId, MultipartFile file, String planText, String authorization);

    /**
     * 将拆解预览的 Goal 与 Tasks 一并写入数据库。
     */
    StudyGoalDTO.GoalDetail saveGoal(Long userId, StudyGoalDTO.SaveRequest request);

    /**
     * 勾选/取消勾选任务：同步任务 status 与 is_completed，并自动重算 Goal 进度。
     *
     * @return 更新后的最新目标进度信息
     */
    StudyGoalDTO.GoalView updateTaskCompletion(Long taskId, Boolean isCompleted, Long userId);

    /**
     * 查询指定目标详情，filter 支持 all / pending（剩余）/ completed（已完成）。
     */
    StudyGoalDTO.GoalDetail getGoalDetail(Long goalId, Long userId, String filter);

    /**
     * 查询指定目标下的剩余任务（is_completed = false）。
     */
    List<StudyGoalDTO.TaskView> getRemainingTasks(Long goalId, Long userId);

    /**
     * 分页查询当前用户的学习计划列表（按更新时间倒序），用于退出后找回历史计划。
     */
    PageResponse<StudyGoalDTO.GoalSummary> listMyGoals(Long userId, Integer page, Integer size);
}
