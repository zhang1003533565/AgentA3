package com.example.appbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FlowchartDTO {
    @Data
    public static class GenerateRequest {
        private String description;
        private String content;
        private String sceneType = "ADMIN";
        private String processType = "ADMIN";
        private String diagramType = "AUTO";
        private String nodeGranularity = "AUTO";
        private String nodeLevel = "AUTO";
        private String decisionMode = "AUTO";
        private String swimlaneMode = "AUTO";
        private String swimlane = "AUTO";
        private List<String> displayItems = new ArrayList<>();
        private List<FileRef> files = new ArrayList<>();

        // Filled after document parsing; optional for plain-text requests.
        private String sourceText;
        private String fileId;
        private String sourceFile;
    }

    @Data
    public static class FileRef {
        private String id;
        private String name;
        private String url;
        private Long size;
    }

    @Data
    public static class FlowchartData {
        private String title;
        private String type = "FLOWCHART";
        private String sceneType;
        private String nodeGranularity;
        private String requestedDecisionMode;
        private String resolvedDecisionMode;
        private String requestedSwimlaneMode;
        private String resolvedSwimlaneMode;
        private List<Lane> lanes = new ArrayList<>();
        private List<Node> nodes = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();
    }

    @Data
    public static class GenerateResponse {
        private String id;
        private String title;
        private String type;
        private String sceneType;
        private String nodeGranularity;
        private String requestedDecisionMode;
        private String resolvedDecisionMode;
        private String requestedSwimlaneMode;
        private String resolvedSwimlaneMode;
        private List<Lane> lanes = new ArrayList<>();
        private List<Node> nodes = new ArrayList<>();
        private List<Edge> edges = new ArrayList<>();
        private LocalDateTime createTime;
    }

    @Data
    public static class Lane {
        private String id;
        private String label;
        private String type;
        private String name;
        private List<String> nodes = new ArrayList<>();
    }

    @Data
    public static class Node {
        private String id;
        private String label;
        private String name;
        private String description;
        private String type = "process";
        private String laneId;
        private String lane;
        private String condition;
        private String input;
        private String output;
        private String exception;
    }

    @Data
    public static class Edge {
        private String id;
        private String source;
        private String target;
        private String label;
        private String type;
        private String condition;
    }

    @Data
    public static class HistoryItem {
        private String id;
        private String title;
        private LocalDateTime createTime;
        private String type;
        private String description;
        private String sceneType;
        private String nodeGranularity;
        private String requestedDecisionMode;
        private String resolvedDecisionMode;
        private String requestedSwimlaneMode;
        private String resolvedSwimlaneMode;
    }

    @Data
    public static class UploadResponse {
        private String fileId;
        private String fileName;
        private String sourceFile;
        private String text;
    }
}
