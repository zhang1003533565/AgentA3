package com.example.appbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campus_facility")
@Schema(description = "校园设施实体")
public class CampusFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "设施ID", example = "1")
    private Long id;

    @Column(name = "facility_name", nullable = false, length = 100, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '设施名称'")
    @Schema(description = "设施名称", example = "第一学生餐厅", requiredMode = Schema.RequiredMode.REQUIRED)
    private String facilityName;

    @Column(name = "facility_type", nullable = false, columnDefinition = "INT NOT NULL COMMENT '设施类型: 1-餐厅 2-运动场 3-教学楼 4-宿舍'")
    @Schema(description = "设施类型: 1-餐厅 2-运动场 3-教学楼 4-宿舍", example = "1")
    private Integer facilityType;

    @Column(columnDefinition = "TEXT COMMENT '设施描述'")
    @Schema(description = "设施描述", example = "位于学校南门，主要提供快餐服务")
    private String description;

    @Column(length = 200, columnDefinition = "VARCHAR(200) COMMENT '位置描述'")
    @Schema(description = "位置描述", example = "南门东侧100米")
    private String location;

    @Column(precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '经度'")
    @Schema(description = "经度", example = "116.397428")
    private BigDecimal longitude;

    @Column(precision = 10, scale = 7, columnDefinition = "DECIMAL(10,7) COMMENT '纬度'")
    @Schema(description = "纬度", example = "39.90923")
    private BigDecimal latitude;

    @Column(columnDefinition = "TEXT COMMENT '图片列表(JSON数组)'")
    @Schema(description = "图片列表(JSON数组)", example = "[\"https://xxx.com/img1.jpg\",\"https://xxx.com/img2.jpg\"]")
    private String images;

    @Column(name = "create_time", columnDefinition = "DATETIME COMMENT '创建时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "DATETIME COMMENT '更新时间'")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public enum FacilityType {
        RESTAURANT(1, "餐厅"),
        SPORTS_FIELD(2, "运动场"),
        TEACHING_BUILDING(3, "教学楼"),
        DORMITORY(4, "宿舍");

        private final Integer value;
        private final String description;

        FacilityType(Integer value, String description) {
            this.value = value;
            this.description = description;
        }

        public Integer getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static FacilityType fromValue(Integer value) {
            for (FacilityType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }
}
