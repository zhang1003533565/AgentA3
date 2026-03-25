package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "地图标记响应")
public class MarkerResponse {

    @Schema(description = "标记ID")
    private Long id;

    @Schema(description = "设施ID")
    private Long facilityId;

    @Schema(description = "设施类型")
    private Integer facilityType;

    @Schema(description = "设施类型名称")
    private String facilityTypeName;

    @Schema(description = "标记名称")
    private String markerName;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "图标URL")
    private String iconUrl;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "位置描述")
    private String location;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "设施图片列表")
    private List<String> images;
}
