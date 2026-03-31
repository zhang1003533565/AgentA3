package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 地址解析响应（地址 → 坐标）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeocoderResponse {

    /** 坐标（纬度） */
    private BigDecimal latitude;

    /** 坐标（经度） */
    private BigDecimal longitude;

    /** 可信度 0-100，越高越可信 */
    private Integer reliability;

    /** 精度等级（高/中/低） */
    private String level;

    /** 标准格式化地址 */
    private String formattedAddress;
}
