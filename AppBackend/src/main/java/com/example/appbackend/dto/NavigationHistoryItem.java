package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导航历史项")
public class NavigationHistoryItem {

    @Schema(description = "导航记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "起点经度")
    private BigDecimal fromLongitude;

    @Schema(description = "起点纬度")
    private BigDecimal fromLatitude;

    @Schema(description = "目标标记ID")
    private Long toMarkerId;

    @Schema(description = "目标名称")
    private String toMarkerName;

    @Schema(description = "终点经度")
    private BigDecimal toLongitude;

    @Schema(description = "终点纬度")
    private BigDecimal toLatitude;

    @Schema(description = "距离（米）")
    private Double distance;

    @Schema(description = "时长（秒）")
    private Integer duration;

    @Schema(description = "状态：1-进行中 2-已完成 3-已取消")
    private Integer status;

    @Schema(description = "到达时间")
    private LocalDateTime arriveTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
