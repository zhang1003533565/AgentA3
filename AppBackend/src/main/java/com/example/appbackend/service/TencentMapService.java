package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.MapMarker;

import java.math.BigDecimal;
import java.util.List;

/**
 * 腾讯地图服务接口
 */
public interface TencentMapService {

    /**
     * 调用腾讯地图路线规划 API 获取真实导航路线
     *
     * @param fromLongitude 起点经度
     * @param fromLatitude  起点纬度
     * @param toLongitude  终点经度
     * @param toLatitude    终点纬度
     * @param mode          出行方式：walking / driving / bicycling
     * @return 导航路线响应
     */
    NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                      BigDecimal toLongitude, BigDecimal toLatitude, String mode);

    /**
     * 逆地址解析：坐标 → 地址
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @return 地址信息
     */
    ReverseGeocoderResponse reverseGeocode(BigDecimal longitude, BigDecimal latitude);

    /**
     * 地址解析：地址 → 坐标
     *
     * @param address 地址字符串（建议含城市，如「北京市海淀区…」；纯 POI 名可能需配合 region）
     * @param region    限定城市/区域（可选，传给腾讯 {@code region}，提高准确率）
     * @return 坐标信息
     */
    GeocoderResponse geocode(String address, String region);

    /**
     * 关键词 POI 搜索
     *
     * @param keyword     搜索关键词
     * @param region      限定城市/区域（可选，传 city 名或 adcode）
     * @param latitude    中心点纬度（用于距离排序，可选）
     * @param longitude   中心点经度（用于距离排序，可选）
     * @param radius      搜索半径（米，仅在指定中心点时有效，默认 1000）
     * @return POI 列表
     */
    PlaceSearchResponse searchPlaces(String keyword, String region,
                                      BigDecimal latitude, BigDecimal longitude, Integer radius);

    /**
     * 坐标批量转换
     *
     * @param points       待转换的坐标列表
     * @param fromCoordSys 来源坐标系：
     *                     1=GPS(WGS-84) / 2=sogou / 3=百度(BD-09) / 4=GCJ-02(腾讯/高德，默认)
     * @return 转换后的坐标列表（全部输出为 GCJ-02）
     */
    CoordTranslateResponse translateCoords(List<CoordTranslateRequest.CoordPoint> points, Integer fromCoordSys);
}
