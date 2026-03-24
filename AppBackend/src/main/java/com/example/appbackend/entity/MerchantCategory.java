package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "merchant_category")
@Schema(description = "商家分类实体")
public class MerchantCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "分类ID", example = "1")
    private Long id;

    @Column(name = "category_name", nullable = false, length = 50, columnDefinition = "VARCHAR(50) NOT NULL COMMENT '分类名称'")
    @Schema(description = "分类名称", example = "餐厅美食", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryName;

    @Column(name = "category_icon", length = 255, columnDefinition = "VARCHAR(255) COMMENT '分类图标URL'")
    @Schema(description = "分类图标URL", example = "https://cdn.example.com/icons/restaurant.png")
    private String categoryIcon;

    @Column(columnDefinition = "INT DEFAULT 0 COMMENT '排序值（越小越前）'")
    @Schema(description = "排序值（越小越前）", example = "1")
    private Integer sort = 0;

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 2-禁用'")
    @Schema(description = "状态: 1-启用 2-禁用", example = "1")
    private Integer status = 1;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
