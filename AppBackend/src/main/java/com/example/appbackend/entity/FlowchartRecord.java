package com.example.appbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "flowchart_record", indexes = {
        @Index(name = "idx_flowchart_user_time", columnList = "user_id,create_time")
})
public class FlowchartRecord {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "process_type", length = 40)
    private String processType;

    @Column(name = "diagram_type", length = 40)
    private String diagramType;

    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;

    @Column(name = "flow_json", nullable = false, columnDefinition = "LONGTEXT")
    private String flowJson;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
