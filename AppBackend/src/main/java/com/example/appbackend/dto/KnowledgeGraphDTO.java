package com.example.appbackend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class KnowledgeGraphDTO {

    private KnowledgeGraphDTO() {
    }

    @Data
    public static class GraphView {
        private String courseKey;
        private List<NodeView> nodes;
        private List<EdgeView> edges;
        private Summary summary;
    }

    @Data
    public static class NodeView {
        private String id;
        private String title;
        private String description;
        private String group;
        private Integer level;
        private Integer order;
        private String status;
        private BigDecimal score;
        private Integer attemptCount;
        private Integer correctCount;
        private Integer wrongCount;
        private LocalDateTime nextReviewAt;
        private List<String> prerequisiteIds;
        private Boolean onActivePath;
        private Long pathItemId;
        private String pathObjective;
    }

    @Data
    public static class EdgeView {
        private String source;
        private String target;
        private String relation;
    }

    @Data
    public static class Summary {
        private Integer total;
        private Integer mastered;
        private Integer learning;
        private Integer weak;
        private Integer available;
        private Integer locked;
        private Integer dueForReview;
    }
}
