package com.example.appbackend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MindMapDTO {

    @Data
    public static class GenerateRequest {
        @Size(max = 4000)
        private String topic;

        @Size(max = 40)
        private String centerTopic;

        @Size(max = 20)
        private String centerTopicMode;

        @Size(max = 20)
        private String depth;

        @Size(max = 60)
        private String structure;

        @Size(max = 40)
        private String detail;

        @Size(max = 64)
        private String fileId;

        @Size(max = 1000)
        private String sourceFile;

        private String sourceText;
    }

    @Data
    public static class MindMapData {
        private String title;
        private String requestedCenterTopicMode;
        private String resolvedCenterTopic;
        private String requestedDepth;
        private Integer resolvedDepth;
        private String requestedStructure;
        private String resolvedStructure;
        private String detailLevel;
        private List<Node> nodes = new ArrayList<>();
    }

    @Data
    public static class Node {
        private String name;
        private List<Node> children = new ArrayList<>();
    }

    @Data
    public static class GenerateResponse {
        private String id;
        private String title;
        private String requestedCenterTopicMode;
        private String resolvedCenterTopic;
        private String requestedDepth;
        private Integer resolvedDepth;
        private String requestedStructure;
        private String resolvedStructure;
        private String detailLevel;
        private List<Node> nodes = new ArrayList<>();
        private LocalDateTime createTime;
        private String content;
        private String sourceType;
        private String sourceFile;
        private String sourceText;
        private String fileId;
        private String fileSummary;
    }

    @Data
    public static class UploadResponse {
        private String fileId;
        private String fileName;
        private String sourceFile;
        private String text;
        private String summary;
        private String summaryStatus;
        private String summaryModel;
        private String centerTopic;
        private String centerTopicStatus;
        private Integer textLength;
        private Boolean truncated;
        private Integer pageCount;
        private Integer slideCount;
        private Integer paragraphCount;
    }

    @Data
    public static class OptimizeRequest {
        private MindMapData currentMindMap;
        @Size(max = 500)
        private String userInstruction;
        private String content;
        private String sourceType;
        private String sourceFile;
        private String sourceText;
        private String fileId;
        private String fileSummary;
    }

    @Data
    public static class HistoryItem {
        private String id;
        private String title;
        private String requestedCenterTopicMode;
        private String resolvedCenterTopic;
        private String requestedDepth;
        private Integer resolvedDepth;
        private String requestedStructure;
        private String resolvedStructure;
        private String detailLevel;
        private LocalDateTime createTime;
        private String preview;
        private String content;
        private String sourceType;
        private String sourceFile;
        private String sourceText;
        private String fileId;
        private String fileSummary;
    }
}
