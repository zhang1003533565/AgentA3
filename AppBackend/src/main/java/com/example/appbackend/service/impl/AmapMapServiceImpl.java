package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AmapMapService;
import com.example.appbackend.util.GeoUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AmapMapServiceImpl implements AmapMapService {

    @Autowired
    @Qualifier("amapWebClient")
    private WebClient amapWebClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${amap.web.key:}")
    private String amapWebKey;

    @Value("${amap.web.base-url:https://restapi.amap.com}")
    private String amapBaseUrl;

    @Override
    public NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                            BigDecimal toLongitude, BigDecimal toLatitude, String mode) {
        ensureKey();
        BigDecimal[] fromNorm = GeoUtils.normalizeChinaLatLng(fromLatitude, fromLongitude);
        BigDecimal[] toNorm = GeoUtils.normalizeChinaLatLng(toLatitude, toLongitude);
        String effectiveMode = normalizeMode(mode);

        String path = switch (effectiveMode) {
            case "driving" -> "/v3/direction/driving";
            case "bicycling" -> "/v4/direction/bicycling";
            default -> "/v3/direction/walking";
        };

        String json = amapWebClient.get()
                .uri(uriBuilder -> buildRouteUri(uriBuilder, path, effectiveMode, fromNorm, toNorm))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = readJson(json, "高德路线规划");
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(502, "高德路线规划失败: " + root.path("info").asText("无响应"));
        }

        JsonNode paths = root.path("route").path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw new BusinessException(404, "高德未找到可用路线");
        }

        JsonNode pathNode = paths.get(0);
        List<RoutePoint> fullPolyline = parsePolyline(pathNode.path("steps"));
        List<RouteStep> steps = parseRouteSteps(pathNode.path("steps"));

        return new NavigationRouteResponse(
                pathNode.path("distance").asDouble(0),
                pathNode.path("duration").asInt(0),
                effectiveMode,
                steps,
                fullPolyline
        );
    }

    @Override
    public ReverseGeocoderResponse reverseGeocode(BigDecimal longitude, BigDecimal latitude) {
        ensureKey();
        BigDecimal[] fixed = GeoUtils.normalizeChinaLatLng(latitude, longitude);
        String json = amapWebClient.get()
                .uri(uriBuilder -> baseUri(uriBuilder, "/v3/geocode/regeo")
                        .queryParam("key", amapWebKey)
                        .queryParam("location", formatLngLat(fixed[1], fixed[0]))
                        .queryParam("extensions", "base")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = readJson(json, "高德逆地理编码");
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(502, "高德逆地理编码失败: " + root.path("info").asText("无响应"));
        }

        JsonNode regeocode = root.path("regeocode");
        JsonNode addressComponent = regeocode.path("addressComponent");
        JsonNode streetNumber = addressComponent.path("streetNumber");

        ReverseGeocoderResponse response = new ReverseGeocoderResponse();
        response.setFormattedAddress(regeocode.path("formatted_address").asText(""));
        response.setProvince(addressComponent.path("province").asText(""));
        response.setCity(addressComponent.path("city").isArray()
                ? addressComponent.path("city").isEmpty() ? "" : addressComponent.path("city").get(0).asText("")
                : addressComponent.path("city").asText(""));
        response.setDistrict(addressComponent.path("district").asText(""));
        response.setStreet(streetNumber.path("street").asText(""));
        response.setStreetNumber(streetNumber.path("number").asText(""));
        response.setAdCode(addressComponent.path("adcode").asText(""));
        response.setLongitude(fixed[1]);
        response.setLatitude(fixed[0]);
        return response;
    }

    @Override
    public GeocoderResponse geocode(String address, String region) {
        ensureKey();
        String json = amapWebClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = baseUri(uriBuilder, "/v3/geocode/geo")
                            .queryParam("key", amapWebKey)
                            .queryParam("address", address);
                    if (region != null && !region.isBlank()) {
                        builder.queryParam("city", region);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = readJson(json, "高德地理编码");
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(502, "高德地理编码失败: " + root.path("info").asText("无响应"));
        }

        JsonNode geocodes = root.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw new BusinessException(404, "高德未找到匹配地址");
        }

        JsonNode geocode = geocodes.get(0);
        BigDecimal[] point = parseLocation(geocode.path("location").asText(""));
        return new GeocoderResponse(
                point[1],
                point[0],
                100,
                geocode.path("level").asText(""),
                geocode.path("formatted_address").asText("")
        );
    }

    @Override
    public PlaceSearchResponse searchPlaces(String keyword, String region,
                                            BigDecimal latitude, BigDecimal longitude, Integer radius) {
        ensureKey();
        String json = amapWebClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = baseUri(uriBuilder, "/v3/place/text")
                            .queryParam("key", amapWebKey)
                            .queryParam("keywords", keyword)
                            .queryParam("offset", 20)
                            .queryParam("page", 1)
                            .queryParam("extensions", "base");
                    if (region != null && !region.isBlank()) {
                        builder.queryParam("city", region);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = readJson(json, "高德地点搜索");
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(502, "高德地点搜索失败: " + root.path("info").asText("无响应"));
        }

        List<PlaceSearchResponse.PoiItem> pois = new ArrayList<>();
        JsonNode poiNodes = root.path("pois");
        if (poiNodes.isArray()) {
            for (JsonNode poiNode : poiNodes) {
                BigDecimal[] point = parseLocation(poiNode.path("location").asText(""));
                Integer distance = null;
                if (longitude != null && latitude != null && point[0] != null && point[1] != null) {
                    distance = (int) Math.round(GeoUtils.distanceBetween(
                            longitude.doubleValue(),
                            latitude.doubleValue(),
                            point[0].doubleValue(),
                            point[1].doubleValue()
                    ));
                }
                pois.add(new PlaceSearchResponse.PoiItem(
                        poiNode.path("name").asText(""),
                        poiNode.path("address").asText(""),
                        point[1],
                        point[0],
                        poiNode.path("adname").asText(""),
                        poiNode.path("type").asText(""),
                        distance,
                        poiNode.path("id").asText("")
                ));
            }
        }
        return new PlaceSearchResponse(pois);
    }

    private void ensureKey() {
        if (amapWebKey == null || amapWebKey.isBlank()) {
            throw new BusinessException(500, "未配置高德 Web 服务 Key");
        }
    }

    private UriBuilder baseUri(UriBuilder uriBuilder, String path) {
        return uriBuilder
                .scheme(extractScheme(amapBaseUrl))
                .host(extractHost(amapBaseUrl))
                .path(path);
    }

    private java.net.URI buildRouteUri(UriBuilder uriBuilder, String path, String mode,
                                       BigDecimal[] fromNorm, BigDecimal[] toNorm) {
        UriBuilder builder = baseUri(uriBuilder, path)
                .queryParam("key", amapWebKey)
                .queryParam("origin", formatLngLat(fromNorm[1], fromNorm[0]))
                .queryParam("destination", formatLngLat(toNorm[1], toNorm[0]));
        if ("driving".equals(mode)) {
            builder.queryParam("strategy", 0);
        }
        return builder.build();
    }

    private JsonNode readJson(String json, String scene) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(502, scene + "无响应");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception error) {
            throw new BusinessException(502, scene + "响应解析失败: " + error.getMessage());
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "walking";
        }
        String value = mode.toLowerCase();
        if ("driving".equals(value) || "walking".equals(value) || "bicycling".equals(value)) {
            return value;
        }
        return "walking";
    }

    private String formatLngLat(BigDecimal longitude, BigDecimal latitude) {
        return formatCoord(longitude) + "," + formatCoord(latitude);
    }

    private String formatCoord(BigDecimal coord) {
        return coord != null ? coord.toPlainString() : "0";
    }

    private BigDecimal[] parseLocation(String value) {
        if (value == null || value.isBlank() || !value.contains(",")) {
            return new BigDecimal[]{null, null};
        }
        String[] parts = value.split(",", 2);
        try {
            return new BigDecimal[]{new BigDecimal(parts[0]), new BigDecimal(parts[1])};
        } catch (Exception error) {
            return new BigDecimal[]{null, null};
        }
    }

    private List<RouteStep> parseRouteSteps(JsonNode stepNodes) {
        if (!stepNodes.isArray() || stepNodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<RouteStep> steps = new ArrayList<>();
        for (JsonNode stepNode : stepNodes) {
            List<RoutePoint> points = parsePolyline(stepNode.path("polyline").asText(""));
            RouteStep step = new RouteStep();
            step.setInstruction(stepNode.path("instruction").asText(""));
            step.setDistance(stepNode.path("distance").asDouble(0));
            step.setDuration(stepNode.path("duration").isMissingNode() ? null : stepNode.path("duration").asInt(0));
            if (!points.isEmpty()) {
                step.setStartPoint(points.get(0));
                step.setEndPoint(points.get(points.size() - 1));
            }
            steps.add(step);
        }
        return steps;
    }

    private List<RoutePoint> parsePolyline(JsonNode stepNodes) {
        if (!stepNodes.isArray()) {
            return Collections.emptyList();
        }
        List<RoutePoint> points = new ArrayList<>();
        for (JsonNode stepNode : stepNodes) {
            points.addAll(parsePolyline(stepNode.path("polyline").asText("")));
        }
        return deduplicatePoints(points);
    }

    private List<RoutePoint> parsePolyline(String polyline) {
        if (polyline == null || polyline.isBlank()) {
            return Collections.emptyList();
        }
        String[] pairs = polyline.split(";");
        List<RoutePoint> points = new ArrayList<>();
        for (String pair : pairs) {
            BigDecimal[] point = parseLocation(pair);
            if (point[0] != null && point[1] != null) {
                points.add(new RoutePoint(point[0], point[1]));
            }
        }
        return deduplicatePoints(points);
    }

    private List<RoutePoint> deduplicatePoints(List<RoutePoint> points) {
        if (points.isEmpty()) {
            return points;
        }
        List<RoutePoint> deduped = new ArrayList<>();
        RoutePoint previous = null;
        for (RoutePoint point : points) {
            if (previous == null
                    || previous.getLongitude() == null
                    || previous.getLatitude() == null
                    || point.getLongitude() == null
                    || point.getLatitude() == null
                    || previous.getLongitude().compareTo(point.getLongitude()) != 0
                    || previous.getLatitude().compareTo(point.getLatitude()) != 0) {
                deduped.add(point);
                previous = point;
            }
        }
        return deduped;
    }

    private String extractScheme(String baseUrl) {
        return baseUrl != null && baseUrl.startsWith("http://") ? "http" : "https";
    }

    private String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "restapi.amap.com";
        }
        return baseUrl.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }
}
