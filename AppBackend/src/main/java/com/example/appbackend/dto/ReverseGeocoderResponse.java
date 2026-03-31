package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 逆地址解析响应（坐标 → 地址）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocoderResponse {

    /** 格式化地址，如"北京市朝阳区阜通东大街6号" */
    private String formattedAddress;

    /** 省份/直辖市 */
    private String province;

    /** 城市 */
    private String city;

    /** 区县 */
    private String district;

    /** 街道名称 */
    private String street;

    /** 门牌号 */
    private String streetNumber;

    /** 行政区划代码 */
    private String adCode;

    /** 坐标（纬度） */
    private BigDecimal latitude;

    /** 坐标（经度） */
    private BigDecimal longitude;
}
