package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 腾讯地图地址解析（地址 → 坐标）响应
 * 文档：https://lbs.qq.com/service/webService/webServiceGuide/geocoder/geocoder
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentGeocoderResult {

    private Integer status;
    private String message;

    @JsonProperty("request_id")
    private String requestId;

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        /** 解析出的坐标 */
        private Location location;
        /** 可信度（0-100，越高越可信） */
        private Integer reliability;
        /** 精度等级：信心（高/中/低） */
        private String level;
        /** 相似度（0-1） */
        private Double similarity;
        /** 偏差（米） */
        private Integer deviation;
        /** 解析出的标准地址 */
        @JsonProperty("formatted_addresses")
        private String formattedAddresses;
        /** 地址组成部件 */
        private AddressComponent address_components;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private Double lat;
        private Double lng;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddressComponent {
        private String province;
        private String city;
        private String district;
        private String street;
        private String street_number;
    }
}
