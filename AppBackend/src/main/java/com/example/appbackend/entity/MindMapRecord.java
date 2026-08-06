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
@Table(name = "mind_map_record", indexes = {
        @Index(name = "idx_mind_map_user_time", columnList = "user_id,create_time")
})
public class MindMapRecord {
    public static final String SOURCE_TEXT = "TEXT";
    public static final String SOURCE_FILE = "FILE";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType = SOURCE_TEXT;

    @Column(name = "source_file", length = 1000)
    private String sourceFile;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "mind_map_json", nullable = false, columnDefinition = "LONGTEXT")
    private String mindMapJson;

    @Column(length = 20)
    private String depth;

    @Column(name = "structure_type", length = 60)
    private String structureType;

    @Column(name = "detail_level", length = 40)
    private String detailLevel;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
