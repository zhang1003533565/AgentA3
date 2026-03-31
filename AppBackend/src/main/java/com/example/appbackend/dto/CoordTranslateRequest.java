package com.example.appbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 坐标批量转换请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoordTranslateRequest {

    @NotEmpty(message = "坐标点列表不能为空")
    private List<CoordPoint> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordPoint {
        /** 纬度 */
        private BigDecimal latitude;
        /** 经度 */
        private BigDecimal longitude;
    }
}
