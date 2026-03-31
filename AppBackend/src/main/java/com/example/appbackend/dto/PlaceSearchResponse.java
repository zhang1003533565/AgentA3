package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 关键词 POI 搜索响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSearchResponse {

    private List<PoiItem> pois;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoiItem {
        /** POI 名称 */
        private String title;
        /** 地址 */
        private String address;
        /** 纬度 */
        private BigDecimal latitude;
        /** 经度 */
        private BigDecimal longitude;
        /** 行政区 */
        private String district;
        /** 类型描述 */
        private String typeDesc;
        /** 距检索点的距离（米） */
        private Integer distance;
        /** 腾讯 POI uid */
        private String uid;
    }
}
