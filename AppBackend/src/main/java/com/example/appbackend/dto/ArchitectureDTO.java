package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 架构图相关 DTO。
 */
public class ArchitectureDTO {

    /**
     * 生成请求体，对应前端 POST /api/ai/architecture/generate。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "架构图生成请求")
    public static class GenerateRequest {

        @Schema(description = "系统需求描述", example = "生成一个校园二手交易系统架构图...")
        private String description;

        @Schema(description = "系统类型", example = "WEB")
        private String systemType;

        @Schema(description = "架构模式", example = "FRONT_BACKEND_SEPARATION")
        private String architectureStyle;

        @Schema(description = "架构层级数组", example = "[\"ACCESS\",\"APPLICATION\",\"SERVICE\",\"DATA\"]")
        private List<String> layers;

        @Schema(description = "展示内容数组", example = "[\"FRONTEND\",\"BACKEND\",\"DATABASE\"]")
        private List<String> displayContent;

        @Schema(description = "关系表达", example = "MODULE")
        private String relationType;
    }

    /**
     * 生成响应体，对应前端 data 字段。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "架构图生成响应")
    public static class GenerateResponse {

        @Schema(description = "记录ID")
        private Long id;

        @Schema(description = "架构标题")
        private String title;

        @Schema(description = "架构风格")
        private String style;

        @Schema(description = "副标题")
        private String subtitle;

        @Schema(description = "6 层分层架构（客户端层/接入层/服务层/数据访问层/数据存储层/基础设施层）")
        private List<Object> layers;

        @Schema(description = "右侧第三方服务")
        private List<Object> thirdParty;

        @Schema(description = "底部特性标签")
        private List<String> features;

        @Schema(description = "节点数组（兼容旧格式）")
        private List<Object> nodes;

        @Schema(description = "连线数组（兼容旧格式）")
        private List<Object> edges;

        @Schema(description = "创建时间")
        private String createTime;
    }

    /**
     * 历史记录列表项。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "架构图历史记录")
    public static class HistoryItem {

        @Schema(description = "记录ID")
        private Long id;

        @Schema(description = "架构标题")
        private String title;

        @Schema(description = "系统类型")
        private String type;

        @Schema(description = "创建时间")
        private String createTime;
    }
}
