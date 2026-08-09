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

        @Schema(description = "用户原始输入内容", example = "校园二手交易系统")
        private String content;

        @Schema(description = "上传文件信息数组")
        private List<Object> files;

        @Schema(description = "系统类型", example = "WEB")
        private String systemType;

        @Schema(description = "架构模式", example = "FRONT_BACKEND_SEPARATION")
        private String architectureStyle;

        @Schema(description = "架构层级数组", example = "[\"ACCESS\",\"APPLICATION\",\"SERVICE\",\"DATA\"]")
        private List<String> layers;

        @Schema(description = "是否由 AI 自动分析架构层级", example = "true")
        private Boolean autoArchitectureLayers;

        @Schema(description = "架构层级数组（新版字段）", example = "[\"CLIENT\",\"APPLICATION\",\"SERVICE\",\"DATA\"]")
        private List<String> architectureLayers;

        @Schema(description = "展示内容数组", example = "[\"FRONTEND\",\"BACKEND\",\"DATABASE\"]")
        private List<String> displayContent;

        @Schema(description = "重点展示内容数组（新版字段）", example = "[\"FRONTEND\",\"BACKEND\",\"DATABASE\"]")
        private List<String> focusContents;

        @Schema(description = "关系表达", example = "MODULE")
        private String relationType;

        @Schema(description = "关系表达模式（新版字段）", example = "DATA_FLOW")
        private String relationMode;

        @Schema(description = "文档解析后的文本，可选；填写后 AI 优先基于此生成", example = "")
        private String sourceText;

        @Schema(description = "已上传文件ID，可选", example = "")
        private String fileId;

        @Schema(description = "已上传文件访问URL，可选", example = "")
        private String sourceFile;
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

        @Schema(description = "系统类型")
        private String systemType;

        @Schema(description = "是否由 AI 自动分析架构层级")
        private Boolean autoArchitectureLayers;

        @Schema(description = "架构层级数组")
        private List<String> architectureLayers;

        @Schema(description = "重点展示内容数组")
        private List<String> focusContents;

        @Schema(description = "用户请求的关系表达")
        private String requestedRelationMode;

        @Schema(description = "AI 最终采用的关系表达")
        private String resolvedRelationMode;

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
        private String systemType;

        @Schema(description = "用户请求的关系表达")
        private String requestedRelationMode;

        @Schema(description = "AI 最终采用的关系表达")
        private String resolvedRelationMode;

        @Schema(description = "创建时间")
        private String createTime;
    }

    /**
     * 文档上传解析响应体，对应前端 POST /api/ai/architecture/upload 的 data 字段。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "架构图文档上传解析响应")
    public static class UploadResponse {

        @Schema(description = "文件ID")
        private String fileId;

        @Schema(description = "原始文件名")
        private String fileName;

        @Schema(description = "文件访问URL")
        private String sourceFile;

        @Schema(description = "解析得到的文本内容")
        private String text;
    }
}
