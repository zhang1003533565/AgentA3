package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "设施创建/更新请求")
public class FacilityRequest {

    @NotNull(message = "设施名称不能为空")
    @Schema(description = "设施名称")
    private String facilityName;

    @NotNull(message = "设施类型不能为空")
    @Schema(description = "设施类型：1-餐厅 2-运动场 3-教学楼 4-宿舍")
    private Integer facilityType;

    @Schema(description = "设施描述")
    private String description;

    @Schema(description = "位置描述")
    private String location;

    @NotNull(message = "经度不能为空")
    @Schema(description = "经度")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "图片列表（JSON数组）")
    private String images;
}
