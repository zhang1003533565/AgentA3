package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;

/**
 * 腾讯地图逆地址解析（坐标 → 地址）响应
 * 文档：https://lbs.qq.com/service/webService/webServiceGuide/geocoder/reverseGeocoder
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentReverseGeocoderResult {

    private Integer status;
    private String message;

    @JsonProperty("request_id")
    private String requestId;

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        /** 可能是对象（省市区等）或整段字符串（如「山东省菏泽市成武县辛丁路」） */
        @JsonDeserialize(using = ReverseGeocoderAddressDeserializer.class)
        private AddressComponent address;
        private AdInfo ad_info;
        /** 行政区划中心点坐标 */
        private Location location;
        /**
         * 格式化地址描述（文档为对象：recommend / rough / standard_address；少数场景可能为字符串）。
         */
        @JsonProperty("formatted_addresses")
        @JsonDeserialize(using = FormattedAddressesDeserializer.class)
        private FormattedAddresses formattedAddresses;
        /** 相似地址信息（参考） */
        private SimilarPoi similar_poi;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddressComponent {
        /**
         * 当腾讯返回 address 为纯字符串时填入（与结构化字段互斥或并存）。
         */
        private String fullText;

        private String nation;
        private String province;
        private String city;
        private String district;
        /** 街道名称 */
        private String street;
        /** 街道门牌号 */
        @JsonProperty("street_number")
        private String streetNumber;
        /** 行政区划代码 */
        @JsonProperty("adcode")
        private String adCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FormattedAddresses {
        /** 推荐展示，描述较精确 */
        private String recommend;
        /** 大致位置 */
        private String rough;
        @JsonProperty("standard_address")
        private String standardAddress;

        public String pickBestLine() {
            if (recommend != null && !recommend.isBlank()) {
                return recommend;
            }
            if (standardAddress != null && !standardAddress.isBlank()) {
                return standardAddress;
            }
            if (rough != null && !rough.isBlank()) {
                return rough;
            }
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdInfo {
        private String nation;
        private String province;
        private String city;
        private String district;
        @JsonProperty("adcode")
        private String adCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private Double lat;
        private Double lng;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SimilarPoi {
        /** 名称 */
        private String title;
        /** 地址 */
        private String address;
        /** 坐标 */
        private Location location;
        /** 距离（米） */
        private Integer distance;
    }

    /**
     * 逆地址解析里 {@code address} 有时是 JSON 对象，有时是单个字符串。
     */
    public static class ReverseGeocoderAddressDeserializer extends JsonDeserializer<AddressComponent> {
        @Override
        public AddressComponent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken t = p.currentToken();
            if (t == JsonToken.VALUE_STRING) {
                AddressComponent ac = new AddressComponent();
                ac.setFullText(p.getText());
                return ac;
            }
            if (t == JsonToken.START_OBJECT) {
                return p.getCodec().readValue(p, AddressComponent.class);
            }
            if (t == JsonToken.VALUE_NULL) {
                return null;
            }
            return null;
        }
    }

    /**
     * {@code formatted_addresses} 通常为对象；若偶发为字符串则当作 recommend。
     */
    public static class FormattedAddressesDeserializer extends JsonDeserializer<FormattedAddresses> {
        @Override
        public FormattedAddresses deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken t = p.currentToken();
            if (t == JsonToken.VALUE_STRING) {
                FormattedAddresses fa = new FormattedAddresses();
                fa.setRecommend(p.getText());
                return fa;
            }
            if (t == JsonToken.START_OBJECT) {
                return p.getCodec().readValue(p, FormattedAddresses.class);
            }
            if (t == JsonToken.VALUE_NULL) {
                return null;
            }
            return null;
        }
    }
}
