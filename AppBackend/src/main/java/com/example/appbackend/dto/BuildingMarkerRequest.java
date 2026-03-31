package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员地图点击创建建筑标注请求
 * 点击地图 → 同时创建设施(CampusFacility) + 地图标记(MapMarker)
 */
@Data
@Schema(description = "管理员地图点击创建建筑标注请求")
public class BuildingMarkerRequest {

    @NotBlank(message = "建筑名称不能为空")
    @Schema(description = "建筑名称", example = "图书馆东门")
    private String name;

    @NotNull(message = "建筑类型不能为空")
    @Min(value = 1, message = "建筑类型最小值为1")
    @Max(value = 4, message = "建筑类型最大值为4")
    @Schema(description = "建筑类型：1-餐厅 2-运动场 3-教学楼 4-宿舍")
    private Integer facilityType;

    @NotNull(message = "经度不能为空")
    @Schema(description = "经度", example = "116.397428")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @Schema(description = "纬度", example = "39.90923")
    private BigDecimal latitude;

    @Schema(description = "位置描述", example = "南门东侧100米")
    private String location;

    @Schema(description = "描述信息", example = "新增的临时建筑标注")
    private String description;
}
