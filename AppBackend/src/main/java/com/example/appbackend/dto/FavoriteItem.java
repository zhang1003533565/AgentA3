package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "收藏目的地项")
public class FavoriteItem {

    @Schema(description = "收藏ID")
    private Long id;

    @Schema(description = "标记ID")
    private Long markerId;

    @Schema(description = "标记名称")
    private String markerName;

    @Schema(description = "设施类型")
    private Integer facilityType;

    @Schema(description = "设施类型名称")
    private String facilityTypeName;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private java.time.LocalDateTime createTime;
}
