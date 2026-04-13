package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.MapService;
import com.example.appbackend.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional
public class MapServiceImpl implements MapService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    @Autowired
    private MapConfigRepository mapConfigRepository;

    @Autowired
    private MapMarkerRepository mapMarkerRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private FavoriteDestinationRepository favoriteDestinationRepository;

    @Autowired
    private NavigationLogRepository navigationLogRepository;

    @Override
    public PageResponse<MarkerResponse> getMarkerList(Integer facilityType, String keyword, Integer pageNum, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "sort"));
        Page<MapMarker> page = mapMarkerRepository.findByConditions(facilityType, keyword, pageRequest);
        Map<Long, CampusFacility> facilityMap = buildFacilityMap(page.getContent());
        List<MarkerResponse> items = page.getContent().stream()
                .map(m -> toMarkerResponse(m, facilityMap.get(m.getFacilityId())))
                .collect(Collectors.toList());
        return new PageResponse<>(items, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public MarkerResponse getMarkerDetail(Long id) {
        MapMarker marker = mapMarkerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "标记不存在"));
        CampusFacility facility = facilityRepository.findById(marker.getFacilityId()).orElse(null);
        return toMarkerResponse(marker, facility);
    }

    @Override
    public MarkerResponse createMarker(MarkerRequest request) {
        if (mapMarkerRepository.findByFacilityId(request.getFacilityId()).isPresent()) {
            throw new BusinessException(400, "该设施已存在地图标记");
        }
        CampusFacility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new BusinessException(404, "设施不存在"));
        MapMarker marker = new MapMarker();
        marker.setFacilityId(facility.getId());
        marker.setIconUrl(request.getIconUrl());
        marker.setSort(request.getSort() != null ? request.getSort() : 0);
        marker.setCreateTime(LocalDateTime.now());
        marker.setUpdateTime(LocalDateTime.now());
        return toMarkerResponse(mapMarkerRepository.save(marker), facility);
    }

    @Override
    public MarkerResponse updateMarker(Long id, MarkerRequest request) {
        MapMarker marker = mapMarkerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "标记不存在"));
        CampusFacility facility = null;
        if (request.getFacilityId() != null) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new BusinessException(404, "设施不存在"));
            marker.setFacilityId(request.getFacilityId());
        }
        if (request.getIconUrl() != null) marker.setIconUrl(request.getIconUrl());
        if (request.getSort() != null) marker.setSort(request.getSort());
        marker.setUpdateTime(LocalDateTime.now());
        MapMarker saved = mapMarkerRepository.save(marker);
        if (facility == null) {
            facility = facilityRepository.findById(saved.getFacilityId()).orElse(null);
        }
        return toMarkerResponse(saved, facility);
    }

    @Override
    public void deleteMarker(Long id) {
        if (!mapMarkerRepository.existsById(id)) {
            throw new BusinessException(404, "标记不存在");
        }
        favoriteDestinationRepository.deleteByMarkerId(id);
        navigationLogRepository.deleteByToMarkerId(id);
        mapMarkerRepository.deleteById(id);
    }

    @Override
    public List<MarkerResponse> batchCreateMarker(List<Long> facilityIds) {
        List<MapMarker> existing = mapMarkerRepository.findByFacilityIdIn(facilityIds);
        Set<Long> existingIds = existing.stream().map(MapMarker::getFacilityId).collect(Collectors.toSet());
        List<MapMarker> created = new ArrayList<>();
        for (Long facilityId : facilityIds) {
            if (existingIds.contains(facilityId)) continue;
            CampusFacility facility = facilityRepository.findById(facilityId).orElse(null);
            if (facility == null) continue;
            MapMarker marker = new MapMarker();
            marker.setFacilityId(facility.getId());
            marker.setCreateTime(LocalDateTime.now());
            marker.setUpdateTime(LocalDateTime.now());
            created.add(mapMarkerRepository.save(marker));
        }
        Map<Long, CampusFacility> facilityMap = buildFacilityMap(created);
        return created.stream().map(m -> toMarkerResponse(m, facilityMap.get(m.getFacilityId()))).collect(Collectors.toList());
    }

    @Override
    public List<MarkerIconInfo> getMarkerIcons() {
        return Arrays.asList(
                new MarkerIconInfo(1, "餐厅", "#FF9500", ""),
                new MarkerIconInfo(2, "运动场", "#34C759", ""),
                new MarkerIconInfo(3, "教学楼", "#007AFF", ""),
                new MarkerIconInfo(4, "宿舍", "#AF52DE", "")
        );
    }

    @Override
    public List<MarkerSummaryItem> searchFacilities(String keyword, Integer facilityType, Integer limit) {
        PageRequest pageRequest = PageRequest.of(0, limit != null ? limit : 10, Sort.by(Sort.Direction.ASC, "id"));
        Page<MapMarker> page = mapMarkerRepository.findByConditions(facilityType, keyword, pageRequest);
        Map<Long, CampusFacility> facilityMap = buildFacilityMap(page.getContent());
        return page.getContent().stream().map(m -> {
            CampusFacility f = facilityMap.get(m.getFacilityId());
            MarkerSummaryItem item = new MarkerSummaryItem();
            item.setId(f != null ? f.getId() : null);
            item.setMarkerId(m.getId());
            item.setFacilityId(f != null ? f.getId() : null);
            item.setFacilityType(f != null ? f.getFacilityType() : null);
            item.setFacilityTypeName(getFacilityTypeName(f != null ? f.getFacilityType() : null));
            item.setMarkerName(f != null ? f.getFacilityName() : "");
            item.setLongitude(f != null ? f.getLongitude() : null);
            item.setLatitude(f != null ? f.getLatitude() : null);
            item.setDistance(null);
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public LocateResponse locate(String keyword) {
        PageRequest pageRequest = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id"));
        Page<MapMarker> page = mapMarkerRepository.findByConditions(null, keyword, pageRequest);
        if (page.isEmpty()) {
            throw new BusinessException(404, "未找到匹配的目的地");
        }
        MapMarker marker = page.getContent().get(0);
        CampusFacility facility = facilityRepository.findById(marker.getFacilityId()).orElse(null);
        LocateResponse resp = new LocateResponse();
        resp.setMarkerId(marker.getId());
        resp.setFacilityId(facility != null ? facility.getId() : null);
        resp.setMarkerName(facility != null ? facility.getFacilityName() : "");
        resp.setLongitude(facility != null ? facility.getLongitude() : null);
        resp.setLatitude(facility != null ? facility.getLatitude() : null);
        resp.setZoomLevel(18);
        return resp;
    }

    @Override
    public List<MarkerResponse> getMarkersByType(Integer facilityType) {
        PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "sort"));
        Page<MapMarker> page = mapMarkerRepository.findByConditions(facilityType, null, pageRequest);
        Map<Long, CampusFacility> facilityMap = buildFacilityMap(page.getContent());
        return page.getContent().stream()
                .map(m -> toMarkerResponse(m, facilityMap.get(m.getFacilityId())))
                .collect(Collectors.toList());
    }

    @Override
    public NearbyResponse getNearbyList(Double longitude, Double latitude, Double radius, Integer facilityType, Integer limit, String sortBy) {
        double effectiveRadius = radius != null ? radius : 500.0;
        int effectiveLimit = limit != null ? limit : 20;

        // 用 bounding-box 在数据库层预过滤，减少全量加载量
        double[] bbox = haversineBoundingBox(latitude, longitude, effectiveRadius);
        List<CampusFacility> facilities = facilityRepository.findNearby(
                facilityType, bbox[0], bbox[1], bbox[2], bbox[3]);

        // 在 bounding-box 内用 Haversine 精确过滤
        List<MarkerSummaryItem> nearbyItems = facilities.stream()
                .filter(f -> {
                    double dist = GeoUtils.distanceBetween(
                            longitude, latitude,
                            f.getLongitude().doubleValue(), f.getLatitude().doubleValue());
                    return dist <= effectiveRadius;
                })
                .map(f -> {
                    double dist = GeoUtils.distanceBetween(
                            longitude, latitude,
                            f.getLongitude().doubleValue(), f.getLatitude().doubleValue());
                    MarkerSummaryItem item = new MarkerSummaryItem();
                    item.setId(f.getId());
                    item.setFacilityId(f.getId());
                    item.setFacilityType(f.getFacilityType());
                    item.setFacilityTypeName(getFacilityTypeName(f.getFacilityType()));
                    item.setMarkerName(f.getFacilityName());
                    item.setLongitude(f.getLongitude());
                    item.setLatitude(f.getLatitude());
                    item.setDistance(dist);
                    item.setLocation(f.getLocation());
                    item.setDescription(f.getDescription());
                    return item;
                })
                .sorted((a, b) -> {
                    if ("name".equalsIgnoreCase(sortBy)) {
                        return a.getMarkerName().compareTo(b.getMarkerName());
                    }
                    return Double.compare(a.getDistance(), b.getDistance());
                })
                .limit(effectiveLimit)
                .collect(Collectors.toList());

        NearbyResponse resp = new NearbyResponse();
        resp.setCenterLongitude(longitude);
        resp.setCenterLatitude(latitude);
        resp.setRadius(effectiveRadius);
        resp.setTotal(nearbyItems.size());
        resp.setList(nearbyItems);
        return resp;
    }

    @Override
    public NearbyCountResponse getNearbyCount(Double longitude, Double latitude, Double radius) {
        double effectiveRadius = radius != null ? radius : 1000.0;

        double[] bbox = haversineBoundingBox(latitude, longitude, effectiveRadius);
        List<CampusFacility> allFacilities = facilityRepository.findNearby(
                null, bbox[0], bbox[1], bbox[2], bbox[3]);

        Map<Integer, List<CampusFacility>> byType = allFacilities.stream()
                .filter(f -> {
                    double dist = GeoUtils.distanceBetween(
                            longitude, latitude,
                            f.getLongitude().doubleValue(), f.getLatitude().doubleValue());
                    return dist <= effectiveRadius;
                })
                .collect(Collectors.groupingBy(CampusFacility::getFacilityType));

        List<CountItem> statistics = byType.entrySet().stream()
                .map(e -> new CountItem(e.getKey(), getFacilityTypeName(e.getKey()), e.getValue().size()))
                .sorted(Comparator.comparing(CountItem::getFacilityType))
                .collect(Collectors.toList());

        NearbyCountResponse resp = new NearbyCountResponse();
        resp.setTotal(statistics.stream().mapToInt(CountItem::getCount).sum());
        resp.setStatistics(statistics);
        return resp;
    }

    /**
     * Haversine 公式反算 bounding-box（度）。
     * 返回 [latMin, latMax, lonMin, lonMax]。
     */
    private double[] haversineBoundingBox(double lat, double lon, double radiusMeters) {
        double earthRadius = 6371000.0;
        double latDelta = Math.toDegrees(radiusMeters / earthRadius);
        double lonDelta = Math.toDegrees(radiusMeters / earthRadius / Math.cos(Math.toRadians(lat)));
        return new double[]{lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta};
    }

    @Override
    public MarkerResponse createBuildingMarker(BuildingMarkerRequest request) {
        CampusFacility facility = new CampusFacility();
        facility.setFacilityName(request.getName());
        facility.setFacilityType(request.getFacilityType());
        facility.setLongitude(request.getLongitude());
        facility.setLatitude(request.getLatitude());
        facility.setLocation(request.getLocation());
        facility.setDescription(request.getDescription());
        facility.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        CampusFacility saved = facilityRepository.save(facility);

        MapMarker marker = new MapMarker();
        marker.setFacilityId(saved.getId());
        marker.setCreateTime(LocalDateTime.now());
        marker.setUpdateTime(LocalDateTime.now());
        MapMarker savedMarker = mapMarkerRepository.save(marker);

        return toMarkerResponse(savedMarker, saved);
    }

    private Map<Long, CampusFacility> buildFacilityMap(List<MapMarker> markers) {
        if (markers.isEmpty()) return Collections.emptyMap();
        List<Long> ids = markers.stream()
                .map(MapMarker::getFacilityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<CampusFacility> facilities = facilityRepository.findByIdIn(ids);
        return facilities.stream().collect(Collectors.toMap(CampusFacility::getId, f -> f));
    }

    private MarkerResponse toMarkerResponse(MapMarker m, CampusFacility f) {
        MarkerResponse resp = new MarkerResponse();
        resp.setId(m.getId());
        resp.setFacilityId(f != null ? f.getId() : null);
        resp.setFacilityType(f != null ? f.getFacilityType() : null);
        resp.setFacilityTypeName(getFacilityTypeName(f != null ? f.getFacilityType() : null));
        resp.setMarkerName(f != null ? f.getFacilityName() : "");
        resp.setLongitude(f != null ? f.getLongitude() : null);
        resp.setLatitude(f != null ? f.getLatitude() : null);
        resp.setImageX(f != null ? f.getImageX() : null);
        resp.setImageY(f != null ? f.getImageY() : null);
        resp.setIconUrl(m.getIconUrl());
        resp.setDescription(f != null ? f.getDescription() : "");
        resp.setLocation(f != null ? f.getLocation() : "");
        resp.setSort(m.getSort());
        resp.setCreateTime(m.getCreateTime());
        resp.setUpdateTime(m.getUpdateTime());
        resp.setStatus(f != null ? f.getStatus() : 1);
        if (f != null && f.getImages() != null && !f.getImages().isBlank()) {
            try {
                resp.setImages(OBJECT_MAPPER.readValue(f.getImages(), STRING_LIST_TYPE));
            } catch (Exception e) {
                resp.setImages(Collections.emptyList());
            }
        } else {
            resp.setImages(Collections.emptyList());
        }
        return resp;
    }

    private Double getConfigDouble(String key, Double defaultVal) {
        return mapConfigRepository.findByConfigKey(key)
                .map(c -> {
                    try { return Double.parseDouble(c.getConfigValue()); }
                    catch (Exception e) { return defaultVal; }
                }).orElse(defaultVal);
    }

    private Integer getConfigInt(String key, Integer defaultVal) {
        return mapConfigRepository.findByConfigKey(key)
                .map(c -> {
                    try { return Integer.parseInt(c.getConfigValue()); }
                    catch (Exception e) { return defaultVal; }
                }).orElse(defaultVal);
    }

    private String getConfigString(String key, String defaultVal) {
        return mapConfigRepository.findByConfigKey(key)
                .map(MapConfig::getConfigValue)
                .orElse(defaultVal);
    }

    private Object parseJsonConfig(String key) {
        String raw = getConfigString(key, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    private void saveOrUpdateConfig(String key, String value) {
        MapConfig config = mapConfigRepository.findByConfigKey(key).orElseGet(() -> {
            MapConfig c = new MapConfig();
            c.setConfigKey(key);
            c.setCreateTime(LocalDateTime.now());
            c.setUpdateTime(LocalDateTime.now());
            return c;
        });
        config.setConfigValue(value);
        config.setUpdateTime(LocalDateTime.now());
        mapConfigRepository.save(config);
    }

    private String getFacilityTypeName(Integer type) {
        if (type == null) return "";
        switch (type) {
            case 1: return "餐厅";
            case 2: return "运动场";
            case 3: return "教学楼";
            case 4: return "宿舍";
            default: return "";
        }
    }
}
