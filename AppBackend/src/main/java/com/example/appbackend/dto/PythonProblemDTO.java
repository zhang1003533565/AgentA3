package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Python 在线编程题库 DTO 集合
 */
public class PythonProblemDTO {

    /** 题库列表项（小程序题库页，不含题面与用例） */
    @Data
    @Schema(description = "题目摘要")
    public static class SummaryVO {
        private Long id;
        private Integer number;
        private String title;
        private String difficulty;
        private Double passRate;
        private String submissions;
        private List<String> tags;
        @Schema(description = "是否支持在线判题")
        private Boolean judgeable;
    }

    /** 题目详情（小程序详情页 / 编程页） */
    @Data
    @Schema(description = "题目详情")
    public static class DetailVO {
        private Long id;
        private Integer number;
        private String title;
        private String difficulty;
        private Double passRate;
        private String submissions;
        private List<String> tags;
        private Boolean judgeable;
        private String description;
        private List<Map<String, Object>> examples;
        private String defaultCode;
        @Schema(description = "判题入口函数名，为空表示暂不支持在线判题")
        private String funcName;
        @Schema(description = "测试用例（透传给 /api/code/execute）")
        private List<Map<String, Object>> testcases;
        private List<Long> similarIds;
    }

    /** 管理端列表项（含上下架与审计信息） */
    @Data
    @Schema(description = "管理端题目")
    public static class AdminVO {
        private Long id;
        private Integer number;
        private String title;
        private String difficulty;
        private Double passRate;
        private String submissions;
        private List<String> tags;
        private Boolean judgeable;
        private Boolean enabled;
        private String description;
        private List<Map<String, Object>> examples;
        private String defaultCode;
        private String funcName;
        private List<Map<String, Object>> testcases;
        private List<Long> similarIds;
        @Schema(description = "标准答案(JSON数组: 多解[{name,idea,code,complexity}])，仅供AI辅助参照")
        private List<Map<String, Object>> solution;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 管理端新增 / 编辑入参 */
    @Data
    @Schema(description = "题目保存请求")
    public static class ProblemRequest {
        @Schema(description = "题号（唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer number;
        @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
        private String title;
        @Schema(description = "难度: easy/medium/hard", requiredMode = Schema.RequiredMode.REQUIRED)
        private String difficulty;
        private Double passRate;
        private String submissions;
        private List<String> tags;
        private String description;
        private List<Map<String, Object>> examples;
        private String defaultCode;
        @Schema(description = "判题入口函数名，留空表示暂不支持在线判题")
        private String funcName;
        @Schema(description = "测试用例，元素含 input/expected，可选 mode(set/deepset)/accepts")
        private List<Map<String, Object>> testcases;
        private List<Long> similarIds;
        @Schema(description = "标准答案(JSON数组: 多解[{name,idea,code,complexity}])，仅供AI辅助参照")
        private List<Map<String, Object>> solution;
        private Boolean enabled;
    }

    /** AI 生成题目入参（对话式） */
    @Data
    @Schema(description = "AI 生成题目请求（对话式）")
    public static class AIGenerateRequest {
        @Schema(description = "自然语言出题需求（核心），如：生成 1 道简单题，只准用数组，类似两数之和，我还没学哈希表", requiredMode = Schema.RequiredMode.REQUIRED)
        private String prompt;
        @Schema(description = "主题快捷词（可选，辅助约束）")
        private String topic;
        @Schema(description = "参考题目标题（可选，从题库选择，AI 基于它生成变式）")
        private String referenceTitle;
        @Schema(description = "难度: easy/medium/hard（可选）")
        private String difficulty;
        @Schema(description = "生成数量 1-5（可选，prompt 提到数量时以 prompt 为准）")
        private Integer count;
        @Schema(description = "修订意见（可选，基于上一轮结果调整）")
        private String previousFeedback;
        @Schema(description = "上一轮生成的题目（修订时传入，供 AI 在其基础上调整）")
        private List<Map<String, Object>> previousProblems;
    }

    /** AI 生成题目结果：AI 理解到的出题规格 + 题目预览列表 */
    @Data
    @Schema(description = "AI 生成题目响应")
    public static class AIGenerateResponse {
        @Schema(description = "AI 理解到的出题规格（供用户确认）")
        private Map<String, Object> spec;
        private List<GeneratedProblemVO> problems;
    }

    /** AI 生成题目结果（预览用，未入库） */
    @Data
    @Schema(description = "AI 生成题目预览")
    public static class GeneratedProblemVO {
        private Integer number;
        private String title;
        private String difficulty;
        private List<String> tags;
        private String description;
        private List<Map<String, Object>> examples;
        private String defaultCode;
        private String funcName;
        private List<Map<String, Object>> testcases;
        private List<Map<String, Object>> solution;
        @Schema(description = "用例自校验：参考代码跑自身测试用例的结果。pass=通过 / fail=用例或参考代码存疑 / skip=无参考代码未校验")
        private String selfCheck;
        @Schema(description = "用例自校验未通过时的说明")
        private String selfCheckDetail;
    }
}
