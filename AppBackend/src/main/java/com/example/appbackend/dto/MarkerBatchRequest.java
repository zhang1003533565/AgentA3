package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量创建标记请求")
public class MarkerBatchRequest {

    @Schema(description = "设施ID列表")
    private List<Long> facilityIds;
}
