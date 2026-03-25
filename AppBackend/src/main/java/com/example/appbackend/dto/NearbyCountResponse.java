package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "周边设施分类统计响应")
public class NearbyCountResponse {

    @Schema(description = "总数量")
    private Integer total;

    @Schema(description = "分类统计列表")
    private List<CountItem> statistics;
}
