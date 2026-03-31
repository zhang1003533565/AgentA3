package com.example.appbackend.util;

import java.math.BigDecimal;

/**
 * 地理计算工具类
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoUtils() {
    }

    /**
     * 计算两点之间的直线距离（米），使用 Haversine 公式。
     * 注意参数顺序：lat 在前，lon 在后。
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * 计算两点之间的直线距离（米），使用 Haversine 公式。
     * 注意：lon1/lon2 在前，lat1/lat2 在后。
     */
    public static double distanceBetween(double lon1, double lat1, double lon2, double lat2) {
        return haversineDistance(lat1, lon1, lat2, lon2);
    }

    /**
     * 中国大陆常见范围：经度约 73–136°，纬度约 15–55°。
     * 若「纬度」字段落在经度区间且「经度」落在纬度区间，视为入库时经纬度写反，交换后返回。
     *
     * @return [latitude, longitude]
     */
    public static BigDecimal[] normalizeChinaLatLng(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return new BigDecimal[]{latitude, longitude};
        }
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        if (looksLikeSwappedChinaMainland(lat, lng)) {
            return new BigDecimal[]{longitude, latitude};
        }
        return new BigDecimal[]{latitude, longitude};
    }

    private static boolean looksLikeSwappedChinaMainland(double latField, double lngField) {
        return latField >= 72.0 && latField <= 136.0
                && lngField >= 15.0 && lngField <= 55.0;
    }
}
