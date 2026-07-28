package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "设施创建/更新请求")
public class FacilityRequest {

    public interface Create {
    }

    @NotNull(message = "设施名称不能为空", groups = Create.class)
    @Schema(description = "设施名称")
    private String facilityName;

    @NotNull(message = "设施类型不能为空", groups = Create.class)
    @Schema(description = "设施类型：1-餐厅 2-运动场 3-教学楼 4-宿舍")
    private Integer facilityType;

    @Schema(description = "设施描述")
    private String description;

    @Schema(description = "设施状态: 1-正常/开放 2-维护中 3-关闭/不可用")
    private Integer status;

    @Schema(description = "位置描述")
    private String location;

    @Schema(description = "经度（通过地图标点设置）")
    private BigDecimal longitude;

    @Schema(description = "纬度（通过地图标点设置）")
    private BigDecimal latitude;

    @Schema(description = "地图图片横向坐标(0-1)，为空时由系统根据经纬度自动计算")
    private BigDecimal imageX;

    @Schema(description = "地图图片纵向坐标(0-1)，为空时由系统根据经纬度自动计算")
    private BigDecimal imageY;

    @Schema(description = "空间形态: POINT-点位 AREA-区域围栏")
    private String geometryType;

    @Schema(description = "区域围栏坐标(JSON二维数组)，AREA 至少需要3个坐标点")
    private String boundaryPoints;

    @Schema(description = "图片列表（JSON数组）")
    private String images;
}
