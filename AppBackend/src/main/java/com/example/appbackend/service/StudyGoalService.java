package com.example.appbackend.service;

import com.example.appbackend.dto.StudyGoalDTO;
import org.springframework.web.multipart.MultipartFile;

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
}
