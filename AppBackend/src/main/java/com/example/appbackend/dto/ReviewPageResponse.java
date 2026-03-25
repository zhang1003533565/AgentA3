package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评价分页响应")
public class ReviewPageResponse {

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "平均评分")
    private Double avgScore;

    @Schema(description = "评分分布")
    private Map<String, Integer> scoreDistribution;

    @Schema(description = "评价列表")
    private List<ReviewListItem> list;
}
