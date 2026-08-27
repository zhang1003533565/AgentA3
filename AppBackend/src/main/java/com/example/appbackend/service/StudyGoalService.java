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

    /** 更新任务部分完成进度，并按预计学习天数重算目标进度。 */
    StudyGoalDTO.GoalView updateTaskProgress(Long taskId, Integer progressPercent, Long userId);

    /** 更新任务状态；completed 会同步任务完成标记与 100% 进度。 */
    StudyGoalDTO.GoalView updateTaskStatus(Long taskId, String status, Long userId);

    /** 更新细分任务完成状态，并自动聚合父任务与目标进度。 */
    StudyGoalDTO.GoalView updateSubtaskCompletion(Long subtaskId, Boolean isCompleted, Long userId);

    /** 更新细分任务部分完成进度，并自动聚合父任务与目标进度。 */
    StudyGoalDTO.GoalView updateSubtaskProgress(Long subtaskId, Integer progressPercent, Long userId);

    /** 更新细分任务状态，并自动聚合父任务与目标进度。 */
    StudyGoalDTO.GoalView updateSubtaskStatus(Long subtaskId, String status, Long userId);

    /** 延后任务及其之后的未完成任务，并返回刷新后的目标详情。 */
    StudyGoalDTO.GoalDetail postponeTask(Long taskId, Integer days, Long userId);

    /** 延后细分任务及其之后的未完成叶子任务，并返回刷新后的目标详情。 */
    StudyGoalDTO.GoalDetail postponeSubtask(Long subtaskId, Integer days, Long userId);

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

    /** 删除当前用户拥有的学习计划及其任务、细分任务。 */
    void deleteGoal(Long goalId, Long userId);
}
