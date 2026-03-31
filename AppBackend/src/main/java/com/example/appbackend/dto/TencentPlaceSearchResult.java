package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 腾讯地图关键词 POI 搜索响应
 * 文档：https://lbs.qq.com/service/webService/webServiceGuide/webServiceSug
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentPlaceSearchResult {

    private Integer status;
    private String message;

    @JsonProperty("request_id")
    private String requestId;

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        /** 关键词建议列表 */
        private List<Suggestion> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Suggestion {
        /** 关键词（名称/地址） */
        private String title;
        /** 地址补充信息 */
        private String address;
        /** 类型（0:普通 POI / 1:公交车站 / 2:地铁站 / 3:交叉路口） */
        private Integer type;
        /** 坐标 */
        private Location location;
        /** 行政区域 */
        private String district;
        /** 距检索关键词的距离（仅在 nearby / region 时有） */
        private Integer distance;
        /** 完整 uid */
        private String uid;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private Double lat;
        private Double lng;
    }
}
