package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FlowchartDTO {
    @Data
    public static class GenerateRequest {
        @NotBlank(message = "请输入流程描述")
        private String description;
        private String processType = "BUSINESS";
        private String diagramType = "AUTO";
        private String nodeLevel = "AUTO";
        private String decisionMode = "AUTO";
        private String swimlane = "AUTO";
        private List<String> displayItems = new ArrayList<>();

        // Filled after document parsing; optional for plain-text requests.
        private String sourceText;
        private String fileId;
        private String sourceFile;
    }

    @Data
    public static class FlowchartData {
        private String title;
        private String type = "FLOWCHART";
        private List<Lane> lanes = new ArrayList<>();
        private List<Node> nodes = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();
    }

    @Data
    public static class GenerateResponse {
        private String id;
        private String title;
        private String type;
        private List<Lane> lanes = new ArrayList<>();
        private List<Node> nodes = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();
        private LocalDateTime createTime;
    }

    @Data
    public static class Lane {
        private String name;
        private List<String> nodes = new ArrayList<>();
    }

    @Data
    public static class Node {
        private String id;
        private String name;
        private String type = "action";
        private String lane;
        private String condition;
        private String input;
        private String output;
        private String exception;
    }

    @Data
    public static class Edge {
        private String source;
        private String target;
        private String label;
        private String condition;
    }

    @Data
    public static class HistoryItem {
        private String id;
        private String title;
        private LocalDateTime createTime;
        private String type;
    }

    @Data
    public static class UploadResponse {
        private String fileId;
        private String fileName;
        private String sourceFile;
        private String text;
    }
}
