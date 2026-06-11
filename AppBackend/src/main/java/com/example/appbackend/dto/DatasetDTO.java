package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class DatasetDTO {

    @Data
    @Schema(description = "知识库创建/更新请求")
    public static class CreateRequest {
        @NotBlank(message = "知识库名称不能为空")
        private String name;

        private String description;

        @Schema(description = "索引技术：high_quality / economy")
        private String indexingTechnique;

        @Schema(description = "向量模型 ID")
        private String embeddingModel;

        @Schema(description = "向量模型服务商")
        private String embeddingModelProvider;

        @Schema(description = "检索模型配置 JSON")
        private String retrievalModel;

        @Schema(description = "文档切分形态：text_model / qa_model / hierarchical_model")
        private String chunkStructure;

        @Schema(description = "权限：only_me / all_team")
        private String permission;
    }

    @Data
    @Schema(description = "知识库详情")
    public static class DatasetVO {
        private Long id;
        private String name;
        private String description;
        private String provider;
        private String permission;
        private String indexingTechnique;
        private String embeddingModel;
        private String embeddingModelProvider;
        private String retrievalModel;
        private String chunkStructure;
        private Integer documentCount;
        private Long wordCount;
        private Long createdBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        /** 关联的最新处理规则 */
        private ProcessRuleVO processRule;
    }

    @Data
    @Schema(description = "知识库列表项")
    public static class DatasetListItem {
        private Long id;
        private String name;
        private String description;
        private String indexingTechnique;
        private String embeddingModel;
        private String chunkStructure;
        private Integer documentCount;
        private Long wordCount;
        private Long segmentCount;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Schema(description = "处理规则创建请求")
    public static class ProcessRuleRequest {
        @Schema(description = "规则模式：automatic / custom / hierarchical")
        private String mode;

        @Schema(description = "处理规则 JSON 配置")
        private String rules;
    }

    @Data
    @Schema(description = "处理规则详情")
    public static class ProcessRuleVO {
        private Long id;
        private Long datasetId;
        private String mode;
        private String rules;
        private Long createdBy;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "文档创建请求")
    public static class DocumentCreateRequest {
        @NotBlank(message = "文档名称不能为空")
        private String name;

        @Schema(description = "数据来源类型：upload_file / text_input")
        private String dataSourceType;

        @Schema(description = "文本内容（text_input 模式使用）")
        private String content;

        @Schema(description = "文件 Base64 内容（upload_file 模式使用）")
        private String contentBase64;

        @Schema(description = "文档形态：text_model / qa_model")
        private String docForm;

        @Schema(description = "文档类型")
        private String docType;

        @Schema(description = "文档元数据 JSON（标签、场景等）")
        private String docMetadata;

        @Schema(description = "处理规则模式：automatic / custom / hierarchical")
        private String processMode;

        @Schema(description = "处理规则 JSON 配置")
        private String processRules;

        @Schema(description = "向量模型配置前缀")
        private String embeddingModel;
    }

    @Data
    @Schema(description = "文档详情")
    public static class DocumentVO {
        private Long id;
        private Long datasetId;
        private String datasetName;
        private Integer position;
        private String name;
        private String dataSourceType;
        private String dataSourceInfo;
        private String indexingStatus;
        private String docForm;
        private String docType;
        private String docMetadata;
        private Integer wordCount;
        private Integer tokens;
        private Integer segmentCount;
        private String batch;
        private Long processRuleId;
        private Integer enabled;
        private Integer archived;
        private Integer hitCount;
        private String errorMessage;
        private LocalDateTime indexingStartedAt;
        private LocalDateTime parsingCompletedAt;
        private LocalDateTime cleaningCompletedAt;
        private LocalDateTime splittingCompletedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Schema(description = "文档列表项")
    public static class DocumentListItem {
        private Long id;
        private Long datasetId;
        private String name;
        private String dataSourceType;
        private String indexingStatus;
        private String docForm;
        private Integer wordCount;
        private Integer segmentCount;
        private Integer enabled;
        private Integer archived;
        private Integer hitCount;
        private String errorMessage;
        private LocalDateTime completedAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Schema(description = "分段详情")
    public static class SegmentVO {
        private Long id;
        private Long datasetId;
        private Long documentId;
        private String documentName;
        private Integer position;
        private String content;
        private String answer;
        private Integer wordCount;
        private Integer tokens;
        private String keywords;
        private String indexNodeId;
        private Integer hitCount;
        private Integer enabled;
        private String status;
        private String errorMessage;
        private String attachments;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private List<ChildChunkVO> childChunks;
    }

    @Data
    @Schema(description = "分段列表项")
    public static class SegmentListItem {
        private Long id;
        private Long documentId;
        private String documentName;
        private Integer position;
        private String content;
        private String answer;
        private Integer wordCount;
        private Integer hitCount;
        private Integer enabled;
        private String status;
        private String keywords;
        private String attachments;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "分段更新请求")
    public static class SegmentUpdateRequest {
        private String content;
        private String answer;
        private String keywords;
        private String attachments;
        private Integer enabled;
    }

    @Data
    @Schema(description = "文档重命名请求")
    public static class RenameRequest {
        @NotBlank(message = "文档名称不能为空")
        private String name;
    }

    @Data
    @Schema(description = "重试失败文档请求")
    public static class RetryRequest {
        private List<Long> documentIds;
    }

    @Data
    @Schema(description = "批量分段操作请求")
    public static class BatchSegmentActionRequest {
        private List<Long> segmentIds;
    }

    @Data
    @Schema(description = "手动创建分段请求")
    public static class CreateSegmentRequest {
        @NotBlank(message = "分段内容不能为空")
        private String content;

        @Schema(description = "QA 模式的回答内容")
        private String answer;

        @Schema(description = "关键词列表")
        private List<String> keywords;

        @Schema(description = "附件列表（图片等），每项包含 name 和 contentBase64")
        private List<AttachmentItem> attachments;
    }

    @Data
    @Schema(description = "附件项")
    public static class AttachmentItem {
        @Schema(description = "附件名称")
        private String name;

        @Schema(description = "附件 Base64 内容")
        private String contentBase64;

        @Schema(description = "附件类型，如 image/png")
        private String type;
    }

    @Data
    @Schema(description = "子片段详情")
    public static class ChildChunkVO {
        private Long id;
        private Long segmentId;
        private Long documentId;
        private Long datasetId;
        private Integer position;
        private String content;
        private Integer wordCount;
        private String indexNodeId;
        private String type;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "子片段创建/更新请求")
    public static class ChildChunkRequest {
        @NotBlank(message = "子片段内容不能为空")
        private String content;
    }
}
