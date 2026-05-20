package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Min(value = 1, message = "建筑类型编码必须大于 0")
    @Schema(description = "建筑类型编码，见 GET /api/v1/facility/types")
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

    @Schema(description = "设施状态: 1-正常/开放 2-维护中 3-关闭/不可用", example = "1")
    private Integer status;
}
