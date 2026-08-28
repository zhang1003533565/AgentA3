package com.example.appbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 智能组卷助手使用的基础字典项。 */
@Data
@Entity
@Table(name = "paper_dictionary",
        uniqueConstraints = @UniqueConstraint(name = "uk_paper_dictionary_type_code", columnNames = {"dict_type", "dict_code"}))
public class PaperDictionary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dict_type", nullable = false, length = 32)
    private String dictType;

    @Column(name = "dict_code", nullable = false, length = 64)
    private String dictCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "creator_id")
    private Long creatorId;

    @PrePersist
    void onCreate() {
        createTime = LocalDateTime.now();
    }
}
