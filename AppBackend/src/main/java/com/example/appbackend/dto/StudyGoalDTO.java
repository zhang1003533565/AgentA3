package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习计划结构化拆解相关传输对象。
 *
 * TaskPlan 为 ai-servers 返回的 snake_case 内部契约；对外统一转成驼峰 TaskView。
 */
public final class StudyGoalDTO {

    private StudyGoalDTO() {
    }

    /** ai-servers /internal/goal-decomposition/decompose 响应（snake_case 契约）。 */
    @Data
    public static class AgentDecomposeResult {
        private AgentGoal goal;

        private List<AgentTask> tasks = new ArrayList<>();
    }

    @Data
    public static class AgentGoal {
        private String title;

        private String description;
    }

    @Data
    public static class AgentTask {
        @JsonProperty("task_name")
        private String taskName;

        private String stage;

        @JsonProperty("estimated_days")
        private Integer estimatedDays;

        private String priority;

        @JsonProperty("order_num")
        private Integer orderNum;

        private String status;

        @JsonProperty("is_completed")
        private Boolean isCompleted;

        private String description;
    }

    /** 上传解析接口响应：拆解预览（未入库）。 */
    @Data
    public static class DecomposeResponse {
        private GoalView goal;

        private List<TaskView> tasks = new ArrayList<>();
    }

    /** 确认入库请求：Goal 与 Tasks 一并写入。 */
    @Data
    public static class SaveRequest {
        private GoalInput goal;

        private List<TaskInput> tasks = new ArrayList<>();
    }

    @Data
    public static class GoalInput {
        private String title;

        private String description;
    }

    @Data
    public static class TaskInput {
        private String taskName;

        private String stage;

        private Integer estimatedDays;

        private String priority;

        private Integer orderNum;

        private Boolean isCompleted;

        private String description;
    }

    /** 目标视图。 */
    @Data
    public static class GoalView {
        private Long id;

        private String title;

        private String description;

        private Integer progress;

        private String status;
    }

    /** 任务视图（前端勾选列表使用）。 */
    @Data
    public static class TaskView {
        private Long id;

        private Long goalId;

        private String taskName;

        private String stage;

        private Integer estimatedDays;

        private String priority;

        private Integer orderNum;

        private String status;

        private Boolean isCompleted;

        private String description;
    }

    /** 目标详情：目标信息 + 任务列表。 */
    @Data
    public static class GoalDetail {
        private GoalView goal;

        private List<TaskView> tasks = new ArrayList<>();
    }
}
