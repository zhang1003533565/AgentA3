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

    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1 COMMENT '设施状态: 1-正常/开放 2-维护中 3-关闭/不可用'")
    @Schema(description = "设施状态: 1-正常/开放 2-维护中 3-关闭/不可用", example = "1")
    private Integer status = 1;

    @Column(columnDefinition = "TEXT COMMENT '设施描述'")
    @Schema(description = "设施描述", example = "位于学校南门，主要提供快餐服务")
    private String description;

    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '材质'")
    @Schema(description = "材质", example = "花岗岩")
    private String material;

    @Column(precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) COMMENT '总高度（米）'")
    @Schema(description = "总高度（米）", example = "3.50")
    private BigDecimal height;

    @Column(precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) COMMENT '重量（吨）'")
    @Schema(description = "重量（吨）", example = "2.80")
    private BigDecimal weight;

    @Column(name = "base_type", length = 100, columnDefinition = "VARCHAR(100) COMMENT '基座形制'")
    @Schema(description = "基座形制", example = "方形基座")
    private String baseType;

    @Column(name = "culture_background", columnDefinition = "TEXT COMMENT '文化背景'")
    @Schema(description = "文化背景")
    private String cultureBackground;

    @Column(name = "culture_highlight_text", length = 100, columnDefinition = "VARCHAR(100) COMMENT '文化背景放大文字'")
    @Schema(description = "文化背景放大文字")
    private String cultureHighlightText;

    @Column(name = "meaning_interpretation", columnDefinition = "TEXT COMMENT '寓意解读'")
    @Schema(description = "寓意解读")
    private String meaningInterpretation;

    @Column(name = "campus_story", columnDefinition = "TEXT COMMENT '校园故事'")
    @Schema(description = "校园故事")
    private String campusStory;

    @Column(length = 200, columnDefinition = "VARCHAR(200) COMMENT '位置描述'")
    @Schema(description = "位置描述", example = "南门东侧100米")
    private String location;

    @Column(precision = 18, scale = 14, columnDefinition = "DECIMAL(18,14) COMMENT '经度'")
    @Schema(description = "经度", example = "116.397428")
    private BigDecimal longitude;

    @Column(precision = 18, scale = 14, columnDefinition = "DECIMAL(18,14) COMMENT '纬度'")
    @Schema(description = "纬度", example = "39.90923")
    private BigDecimal latitude;

    @Column(name = "image_x", precision = 8, scale = 6, columnDefinition = "DECIMAL(8,6) COMMENT '地图图片横向坐标(0-1)'")
    @Schema(description = "地图图片横向坐标(0-1)", example = "0.452100")
    private BigDecimal imageX;

    @Column(name = "image_y", precision = 8, scale = 6, columnDefinition = "DECIMAL(8,6) COMMENT '地图图片纵向坐标(0-1)'")
    @Schema(description = "地图图片纵向坐标(0-1)", example = "0.387500")
    private BigDecimal imageY;

    @Column(name = "geometry_type", nullable = false, length = 16, columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'POINT' COMMENT '空间形态: POINT-点位 AREA-区域围栏'")
    @Schema(description = "空间形态: POINT-点位 AREA-区域围栏", example = "POINT")
    private String geometryType = "POINT";

    @Column(name = "boundary_points", columnDefinition = "TEXT COMMENT '区域围栏坐标(JSON二维数组)'")
    @Schema(description = "区域围栏坐标(JSON二维数组)", example = "[[114.897,40.755],[114.898,40.755],[114.898,40.756]]")
    private String boundaryPoints;

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
        RESTAURANT(1, "食堂"),
        SPORTS_FIELD(2, "运动场"),
        TEACHING_BUILDING(3, "教学楼"),
        COMPREHENSIVE(4, "综合服务"),
        CAMPUS_SHOP(5, "校内商铺"),
        OTHER(99, "其他");

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
            if (value == null) {
                return OTHER;
            }
            for (FacilityType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return OTHER;
        }
    }

    public enum FacilityStatus {
        NORMAL(1, "正常/开放"),
        MAINTENANCE(2, "维护中"),
        CLOSED(3, "关闭/不可用");

        private final Integer value;
        private final String description;

        FacilityStatus(Integer value, String description) {
            this.value = value;
            this.description = description;
        }

        public Integer getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static FacilityStatus fromValue(Integer value) {
            if (value == null) return NORMAL;
            for (FacilityStatus s : values()) {
                if (s.value.equals(value)) return s;
            }
            return NORMAL;
        }
    }
}
