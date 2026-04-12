package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.service.TencentMapService;
import com.example.appbackend.util.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 腾讯地图路线规划服务实现
 *
 * 官方文档：https://lbs.qq.com/service/webService/webServiceGuide/route/webServiceRoute
 *
 * 支持模式：walking / driving / bicycling
 * - distance：米（Integer）
 * - duration：分钟（Integer）→ 对外统一转为秒
 * - polyline：压缩坐标数字数组，解压规则见 decodeCompressedPolyline
 * - steps[].polyline_idx：[起始下标, 终止下标]，指在方案压缩 polyline 数组中的位置
 */
@Service
public class TencentMapServiceImpl implements TencentMapService {

    private static final String WALKING = "walking";
    private static final String DRIVING = "driving";
    private static final String BICYCLING = "bicycling";

    @Autowired
    private WebClient tencentMapWebClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemConfigService systemConfigService;

    @Override
    public NavigationRouteResponse getRoute(
            BigDecimal fromLongitude, BigDecimal fromLatitude,
            BigDecimal toLongitude, BigDecimal toLatitude,
            String mode) {

        String effectiveMode = normalizeMode(mode);

        BigDecimal[] fromNorm = GeoUtils.normalizeChinaLatLng(fromLatitude, fromLongitude);
        BigDecimal[] toNorm = GeoUtils.normalizeChinaLatLng(toLatitude, toLongitude);
        final BigDecimal fromLat = fromNorm[0];
        final BigDecimal fromLon = fromNorm[1];
        final BigDecimal toLat = toNorm[0];
        final BigDecimal toLon = toNorm[1];
        final String tencentMapKey = systemConfigService.getValue("tencent.map.key", "");
        final String baseUrl = systemConfigService.getValue("tencent.map.base-url", "https://apis.map.qq.com");

        String json = tencentMapWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(extractScheme(baseUrl))
                        .host(extractHost(baseUrl))
                        .path("/ws/direction/v1/{mode}")
                        .queryParam("from", formatCoord(fromLat) + "," + formatCoord(fromLon))
                        .queryParam("to", formatCoord(toLat) + "," + formatCoord(toLon))
                        .queryParam("key", tencentMapKey)
                        .build(effectiveMode))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank()) {
            throw new BusinessException(502, "腾讯地图无响应");
        }

        TencentRouteResult result;
        try {
            result = objectMapper.readValue(json, TencentRouteResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "腾讯地图响应解析失败: " + e.getOriginalMessage());
        }

        if (result == null || result.getStatus() == null || result.getStatus() != 0) {
            throw new BusinessException(502,
                    "腾讯地图路线规划请求失败（status=" + (result != null ? result.getStatus() : "null") + "）: "
                            + (result != null ? result.getMessage() : "无响应"));
        }

        TencentRouteResult.Result routeResult = result.getResult();
        if (routeResult == null || routeResult.getRoutes() == null || routeResult.getRoutes().isEmpty()) {
            throw new BusinessException(404, "未找到路线");
        }

        TencentRouteResult.TencentRoute route = routeResult.getRoutes().get(0);

        NavigationRouteResponse resp = new NavigationRouteResponse();
        resp.setMode(effectiveMode);
        resp.setDistance(route.getDistance() != null ? route.getDistance().doubleValue() : 0.0);
        // 文档明确：duration 单位为分钟 → 统一转为秒
        resp.setDuration(route.getDuration() != null ? route.getDuration() * 60 : 0);

        // 全线路坐标
        List<RoutePoint> fullPoints = decodeCompressedPolyline(route.getPolyline());
        resp.setPolyline(fullPoints);

        // 路线步骤
        if (route.getSteps() != null && !route.getSteps().isEmpty()) {
            List<RouteStep> steps = new ArrayList<>();
            for (TencentRouteResult.TencentStep step : route.getSteps()) {
                RouteStep s = new RouteStep();
                s.setInstruction(step.getInstruction());
                s.setDistance(step.getDistance() != null ? step.getDistance().doubleValue() : 0.0);
                s.setDuration(step.getDuration() != null ? step.getDuration() * 60 : null);

                List<RoutePoint> stepPoints = sliceStepPoints(fullPoints, step.getPolylineIdx());
                if (!stepPoints.isEmpty()) {
                    s.setStartPoint(stepPoints.get(0));
                    s.setEndPoint(stepPoints.get(stepPoints.size() - 1));
                }
                steps.add(s);
            }
            resp.setSteps(steps);
        } else {
            resp.setSteps(Collections.emptyList());
        }

        return resp;
    }

    private String normalizeMode(String mode) {
        if (mode == null) return WALKING;
        String lower = mode.toLowerCase();
        return (lower.equals(WALKING) || lower.equals(DRIVING) || lower.equals(BICYCLING)) ? lower : WALKING;
    }

    private String extractScheme(String baseUrl) {
        return baseUrl != null && baseUrl.startsWith("http://") ? "http" : "https";
    }

    private String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "apis.map.qq.com";
        return baseUrl.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private String formatCoord(BigDecimal coord) {
        return coord != null ? coord.toPlainString() : "0";
    }

    // -------------------------------------------------------------------------
    // 官方 polyline 解压（步行/驾车/骑行通用）
    // -------------------------------------------------------------------------
    /**
     * 腾讯地图 WebService 返回的 polyline 为压缩坐标数组，格式为 [lat0, lng0, d1_lat, d1_lng, d2_lat, d2_lng, ...]。
     * <p>
     * 解压规则（官方文档「polyline 坐标解压」）：
     * 自第 3 项起，每项 = 前第二项的相对增量（单位 1e-6 度）。
     * 即：coors[i] = coors[i-2] + coors[i] / 1e6
     * <p>
     * 结果两两一组：[纬度, 经度]，对应一个坐标点。
     * RoutePoint.latitude = 纬度，RoutePoint.longitude = 经度。
     */
    private List<RoutePoint> decodeCompressedPolyline(List<Double> raw) {
        if (raw == null || raw.size() < 2) {
            return Collections.emptyList();
        }

        int n = raw.size();
        double[] coors = new double[n];
        for (int i = 0; i < n; i++) {
            coors[i] = raw.get(i);
        }
        // 从第 3 项起执行增量累加（解压）
        for (int i = 2; i < n; i++) {
            coors[i] = coors[i - 2] + coors[i] / 1_000_000.0;
        }

        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i + 1 < n; i += 2) {
            double lat = coors[i];     // 纬度
            double lng = coors[i + 1]; // 经度
            points.add(new RoutePoint(BigDecimal.valueOf(lng), BigDecimal.valueOf(lat)));
        }
        return points;
    }

    /**
     * 根据 polyline_idx 从全线路坐标中截取该步骤的坐标。
     *
     * polyline_idx = [起始下标, 终止下标]（含），指压缩 polyline 数组中的位置。
     * 由于每 2 个压缩元素组成 1 个坐标点对：
     *   压缩索引 start → RoutePoint 序号 start/2
     *   压缩索引 end   → RoutePoint 序号 end/2
     */
    private List<RoutePoint> sliceStepPoints(List<RoutePoint> fullPoints, List<Integer> polylineIdx) {
        if (polylineIdx == null || polylineIdx.size() < 2
                || fullPoints == null || fullPoints.isEmpty()) {
            return Collections.emptyList();
        }

        int start = polylineIdx.get(0);
        int end = polylineIdx.get(1);

        if (start < 0 || end < start) {
            return Collections.emptyList();
        }

        int firstPair = start / 2;
        int lastPair = end / 2;

        if (firstPair >= fullPoints.size()) {
            return Collections.emptyList();
        }
        lastPair = Math.min(lastPair, fullPoints.size() - 1);
        if (firstPair > lastPair) {
            return Collections.emptyList();
        }

        return new ArrayList<>(fullPoints.subList(firstPair, lastPair + 1));
    }

    // -------------------------------------------------------------------------
    // 逆地址解析：坐标 → 地址
    // 文档：https://lbs.qq.com/service/webService/webServiceGuide/geocoder/reverseGeocoder
    // -------------------------------------------------------------------------
    @Override
    public ReverseGeocoderResponse reverseGeocode(BigDecimal longitude, BigDecimal latitude) {
        BigDecimal[] fixed = GeoUtils.normalizeChinaLatLng(latitude, longitude);
        final BigDecimal latFixed = fixed[0];
        final BigDecimal lngFixed = fixed[1];
        final String tencentMapKey = systemConfigService.getValue("tencent.map.key", "");
        final String baseUrl = systemConfigService.getValue("tencent.map.base-url", "https://apis.map.qq.com");
        String json = tencentMapWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(extractScheme(baseUrl))
                        .host(extractHost(baseUrl))
                        .path("/ws/geocoder/v1/")
                        .queryParam("location", formatCoord(latFixed) + "," + formatCoord(lngFixed))
                        .queryParam("key", tencentMapKey)
                        .queryParam("get_poi", 1)   // 同时返回附近 POI
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank()) {
            throw new BusinessException(502, "腾讯地图逆地址解析无响应");
        }

        TencentReverseGeocoderResult result;
        try {
            result = objectMapper.readValue(json, TencentReverseGeocoderResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "腾讯地图逆地址解析响应解析失败: " + e.getOriginalMessage());
        }

        if (result == null || result.getStatus() == null || result.getStatus() != 0) {
            throw new BusinessException(502, "腾讯地图逆地址解析失败（status="
                    + (result != null ? result.getStatus() : "null") + "）: "
                    + (result != null ? result.getMessage() : "无响应"));
        }

        TencentReverseGeocoderResult.Result r = result.getResult();
        ReverseGeocoderResponse resp = new ReverseGeocoderResponse();
        String formatted = r.getFormattedAddresses() != null ? r.getFormattedAddresses().pickBestLine() : null;
        if (r.getAddress() != null) {
            TencentReverseGeocoderResult.AddressComponent addr = r.getAddress();
            if ((formatted == null || formatted.isBlank())
                    && addr.getFullText() != null && !addr.getFullText().isBlank()) {
                formatted = addr.getFullText();
            }
            resp.setProvince(addr.getProvince());
            resp.setCity(addr.getCity());
            resp.setDistrict(addr.getDistrict());
            resp.setStreet(addr.getStreet());
            resp.setStreetNumber(addr.getStreetNumber());
            resp.setAdCode(addr.getAdCode());
        }
        resp.setFormattedAddress(formatted);
        if (r.getLocation() != null) {
            resp.setLatitude(BigDecimal.valueOf(r.getLocation().getLat()));
            resp.setLongitude(BigDecimal.valueOf(r.getLocation().getLng()));
        }
        return resp;
    }

    // -------------------------------------------------------------------------
    // 地址解析：地址 → 坐标
    // 文档：https://lbs.qq.com/service/webService/webServiceGuide/geocoder/geocoder
    // -------------------------------------------------------------------------
    @Override
    public GeocoderResponse geocode(String address, String region) {
        final String tencentMapKey = systemConfigService.getValue("tencent.map.key", "");
        final String baseUrl = systemConfigService.getValue("tencent.map.base-url", "https://apis.map.qq.com");
        String json = tencentMapWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.scheme(extractScheme(baseUrl))
                            .host(extractHost(baseUrl))
                            .path("/ws/geocoder/v1/")
                            .queryParam("address", address)
                            .queryParam("key", tencentMapKey);
                    if (region != null && !region.isBlank()) {
                        uriBuilder.queryParam("region", region);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank()) {
            throw new BusinessException(502, "腾讯地图地址解析无响应");
        }

        TencentGeocoderResult result;
        try {
            result = objectMapper.readValue(json, TencentGeocoderResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "腾讯地图地址解析响应解析失败: " + e.getOriginalMessage());
        }

        if (result == null || result.getStatus() == null || result.getStatus() != 0) {
            // 348：参数错误——常见为地址不含城市、仅为 POI 名；地点建议接口更适合此类查询
            if (result != null && Integer.valueOf(348).equals(result.getStatus())) {
                GeocoderResponse fromSug = tryGeocodeFromPlaceSuggestion(address, region);
                if (fromSug != null) {
                    return fromSug;
                }
            }
            throw new BusinessException(502, "腾讯地图地址解析失败（status="
                    + (result != null ? result.getStatus() : "null") + "）: "
                    + (result != null ? result.getMessage() : "无响应"));
        }

        TencentGeocoderResult.Result r = result.getResult();
        GeocoderResponse resp = new GeocoderResponse();
        if (r.getLocation() != null) {
            resp.setLatitude(BigDecimal.valueOf(r.getLocation().getLat()));
            resp.setLongitude(BigDecimal.valueOf(r.getLocation().getLng()));
        }
        resp.setReliability(r.getReliability());
        resp.setLevel(r.getLevel());
        resp.setFormattedAddress(r.getFormattedAddresses());
        return resp;
    }

    /**
     * 地理编码失败时的兜底：用关键词建议拿第一条 POI 坐标（与 {@link #searchPlaces} 同源接口）。
     */
    private GeocoderResponse tryGeocodeFromPlaceSuggestion(String keyword, String region) {
        PlaceSearchResponse ps;
        try {
            ps = searchPlaces(keyword, region, null, null, null);
        } catch (BusinessException e) {
            return null;
        }
        if (ps.getPois() == null || ps.getPois().isEmpty()) {
            return null;
        }
        PlaceSearchResponse.PoiItem first = ps.getPois().get(0);
        if (first.getLatitude() == null || first.getLongitude() == null) {
            return null;
        }
        GeocoderResponse resp = new GeocoderResponse();
        resp.setLatitude(first.getLatitude());
        resp.setLongitude(first.getLongitude());
        resp.setReliability(null);
        resp.setLevel("POI建议");
        String line = first.getTitle() != null ? first.getTitle() : "";
        if (first.getAddress() != null && !first.getAddress().isBlank()) {
            line = line.isBlank() ? first.getAddress() : line + " " + first.getAddress();
        }
        resp.setFormattedAddress(line.isBlank() ? null : line.trim());
        return resp;
    }

    // -------------------------------------------------------------------------
    // 关键词 POI 搜索
    // 文档：https://lbs.qq.com/service/webService/webServiceGuide/webServiceSug
    // -------------------------------------------------------------------------
    @Override
    public PlaceSearchResponse searchPlaces(String keyword, String region,
                                            BigDecimal latitude, BigDecimal longitude, Integer radius) {
        StringBuilder uri = new StringBuilder("/ws/place/v1/suggestion");
        final String tencentMapKey = systemConfigService.getValue("tencent.map.key", "");
        final String baseUrl = systemConfigService.getValue("tencent.map.base-url", "https://apis.map.qq.com");
        String json = tencentMapWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.scheme(extractScheme(baseUrl))
                            .host(extractHost(baseUrl))
                            .path(uri.toString())
                            .queryParam("keyword", keyword)
                            .queryParam("key", tencentMapKey);
                    if (region != null && !region.isBlank()) {
                        uriBuilder.queryParam("region", region);
                    }
                    if (latitude != null && longitude != null) {
                        uriBuilder.queryParam("location", formatCoord(latitude) + "," + formatCoord(longitude));
                    }
                    if (radius != null && radius > 0) {
                        uriBuilder.queryParam("radius", radius);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank()) {
            throw new BusinessException(502, "腾讯地图 POI 搜索无响应");
        }

        TencentPlaceSearchResult result;
        try {
            result = objectMapper.readValue(json, TencentPlaceSearchResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "腾讯地图 POI 搜索响应解析失败: " + e.getOriginalMessage());
        }

        if (result == null || result.getStatus() == null || result.getStatus() != 0) {
            throw new BusinessException(502, "腾讯地图 POI 搜索失败（status="
                    + (result != null ? result.getStatus() : "null") + "）: "
                    + (result != null ? result.getMessage() : "无响应"));
        }

        PlaceSearchResponse resp = new PlaceSearchResponse();
        if (result.getResult() != null && result.getResult().getData() != null) {
            List<PlaceSearchResponse.PoiItem> pois = result.getResult().getData().stream()
                    .map(s -> {
                        PlaceSearchResponse.PoiItem item = new PlaceSearchResponse.PoiItem();
                        item.setTitle(s.getTitle());
                        item.setAddress(s.getAddress());
                        item.setDistrict(s.getDistrict());
                        item.setDistance(s.getDistance());
                        item.setUid(s.getUid());
                        item.setTypeDesc(parsePoiType(s.getType()));
                        if (s.getLocation() != null) {
                            item.setLatitude(BigDecimal.valueOf(s.getLocation().getLat()));
                            item.setLongitude(BigDecimal.valueOf(s.getLocation().getLng()));
                        }
                        return item;
                    })
                    .collect(Collectors.toList());
            resp.setPois(pois);
        } else {
            resp.setPois(Collections.emptyList());
        }
        return resp;
    }

    private String parsePoiType(Integer type) {
        if (type == null) return "普通POI";
        return switch (type) {
            case 1 -> "公交车站";
            case 2 -> "地铁站";
            case 3 -> "交叉路口";
            default -> "普通POI";
        };
    }

    // -------------------------------------------------------------------------
    // 坐标批量转换
    // 文档：https://lbs.qq.com/service/webService/webServiceGuide/tool/unit-change
    // -------------------------------------------------------------------------
    /**
     * 坐标系说明：
     *   1 = GPS(WGS-84)      — GPS 设备原始坐标
     *   2 = sogou            — 搜狗坐标
     *   3 = 百度(BD-09)       — 百度地图特有
     *   4 = GCJ-02(默认)      — 腾讯/高德地图标准
     *
     * 输出：全部转为 GCJ-02
     */
    @Override
    public CoordTranslateResponse translateCoords(List<CoordTranslateRequest.CoordPoint> points, Integer fromCoordSys) {
        if (points == null || points.isEmpty()) {
            throw new BusinessException(400, "坐标点列表不能为空");
        }
        if (points.size() > 100) {
            throw new BusinessException(400, "每次最多转换 100 个坐标点，当前：" + points.size());
        }

        // 构造 ; 分隔的坐标字符串：lat,lng;lat,lng;...
        String coords = points.stream()
                .map(p -> formatCoord(p.getLatitude()) + "," + formatCoord(p.getLongitude()))
                .collect(Collectors.joining(";"));

        int type = (fromCoordSys != null && fromCoordSys > 0 && fromCoordSys <= 4) ? fromCoordSys : 4;
        final String tencentMapKey = systemConfigService.getValue("tencent.map.key", "");
        final String baseUrl = systemConfigService.getValue("tencent.map.base-url", "https://apis.map.qq.com");

        String json = tencentMapWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(extractScheme(baseUrl))
                        .host(extractHost(baseUrl))
                        .path("/ws/coord/v1/translate")
                        .queryParam("locations", coords)
                        .queryParam("type", type)
                        .queryParam("key", tencentMapKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank()) {
            throw new BusinessException(502, "腾讯地图坐标转换无响应");
        }

        TencentCoordTranslateResult result;
        try {
            result = objectMapper.readValue(json, TencentCoordTranslateResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "腾讯地图坐标转换响应解析失败: " + e.getOriginalMessage());
        }

        if (result == null || result.getStatus() == null || result.getStatus() != 0) {
            throw new BusinessException(502, "腾讯地图坐标转换失败（status="
                    + (result != null ? result.getStatus() : "null") + "）: "
                    + (result != null ? result.getMessage() : "无响应"));
        }

        CoordTranslateResponse resp = new CoordTranslateResponse();
        if (result.getResult() != null && result.getResult().getLocations() != null) {
            List<CoordTranslateResponse.CoordPoint> translated = result.getResult().getLocations().stream()
                    .map(loc -> new CoordTranslateResponse.CoordPoint(
                            BigDecimal.valueOf(loc.getLat()),
                            BigDecimal.valueOf(loc.getLng())))
                    .collect(Collectors.toList());
            resp.setPoints(translated);
        } else {
            resp.setPoints(Collections.emptyList());
        }
        return resp;
    }
}
