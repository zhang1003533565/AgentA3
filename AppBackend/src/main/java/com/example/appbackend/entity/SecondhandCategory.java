package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "secondhand_category")
@Schema(description = "物品分类实体")
public class SecondhandCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "分类ID", example = "1")
    private Long id;

    @Column(name = "category_name", nullable = false, length = 50, columnDefinition = "VARCHAR(50) NOT NULL COMMENT '分类名称'")
    @Schema(description = "分类名称", example = "数码产品", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryName;

    @Column(columnDefinition = "INT DEFAULT 0 COMMENT '排序值（越小越前）'")
    @Schema(description = "排序值（越小越前）", example = "1")
    private Integer sort = 0;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
