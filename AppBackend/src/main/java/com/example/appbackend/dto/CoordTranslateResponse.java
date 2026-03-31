package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 坐标转换响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoordTranslateResponse {

    /**
     * 转换后的坐标点列表（顺序与输入一致）
     * 全部输出为 GCJ-02 坐标系（腾讯/高德地图标准）
     */
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
