package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 腾讯地图坐标转换 API 响应
 * 文档：https://lbs.qq.com/service/webService/webServiceGuide/tool/unit-change
 *
 * 支持批量转换：最多 100 个坐标点一次性转换。
 * 输入坐标类型（type 参数）：
 *   1  GPS 坐标（设备原始坐标，WGS-84）
 *   2  sogou 经纬度坐标
 *   3  百度经纬度坐标（BD-09）
 *   4  地图经纬度坐标（GCJ-02，默认）
 *
 * 输出固定为 GCJ-02（腾讯/高德地图使用）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentCoordTranslateResult {

    private Integer status;
    private String message;

    @JsonProperty("request_id")
    private String requestId;

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        /** 转换后的坐标数组（顺序与输入一致） */
        private List<Location> locations;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        /** 转换后的纬度（GCJ-02） */
        private Double lat;
        /** 转换后的经度（GCJ-02） */
        private Double lng;
    }
}
