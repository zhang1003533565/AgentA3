package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标记摘要信息（搜索/周边/收藏/统计通用）")
public class MarkerSummaryItem {

    @Schema(description = "设施ID")
    private Long id;

    @Schema(description = "标记ID")
    private Long markerId;

    @Schema(description = "设施ID")
    private Long facilityId;

    @Schema(description = "设施类型")
    private Integer facilityType;

    @Schema(description = "设施类型名称")
    private String facilityTypeName;

    @Schema(description = "标记/设施名称")
    private String markerName;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "距离（米）")
    private Double distance;

    @Schema(description = "图标URL")
    private String iconUrl;

    @Schema(description = "位置描述")
    private String location;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "访问次数")
    private Integer visitCount;

    @Schema(description = "导航次数")
    private Integer navigationCount;

    @Schema(description = "排名")
    private Integer rank;

    @Schema(description = "查看次数")
    private Integer viewCount;
}
