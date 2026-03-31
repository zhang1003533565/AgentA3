package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.MapMarker;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.repository.NavigationLogRepository;
import com.example.appbackend.service.MapStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class MapStatisticsServiceImpl implements MapStatisticsService {

    @Autowired
    private NavigationLogRepository navigationLogRepository;

    @Autowired
    private MapMarkerRepository mapMarkerRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Override
    public NavigationStatisticsResponse getNavigationStatistics(String startDate, String endDate) {
        NavigationStatisticsResponse resp = new NavigationStatisticsResponse();
        resp.setTotalNavigations(navigationLogRepository.countTotal());
        resp.setTodayNavigations(navigationLogRepository.countSince(LocalDate.now().atStartOfDay()));
        resp.setCompletedNavigations(navigationLogRepository.countByStatus(2));
        resp.setCancelledNavigations(navigationLogRepository.countByStatus(3));
        Double avg = navigationLogRepository.avgDuration();
        resp.setAverageDuration(avg != null ? avg.intValue() : 0);
        resp.setPopularDestinations(buildPopularDestinations(5));
        resp.setDailyTrend(Collections.emptyList());
        return resp;
    }

    @Override
    public List<MarkerSummaryItem> getFacilityHeat(Integer facilityType, String startDate, String endDate, Integer limit) {
        int effectiveLimit = limit != null ? limit : 10;
        Object[] rows = navigationLogRepository.findTopDestinations(effectiveLimit);

        Map<Long, MapMarker> markerMap = buildMarkerMap();
        Map<Long, CampusFacility> facilityMap = buildFacilityMap(markerMap.values());

        List<MarkerSummaryItem> items = new ArrayList<>();
        int rank = 1;
        if (rows != null) {
            for (Object row : rows) {
                if (row instanceof Object[]) {
                    Object[] arr = (Object[]) row;
                    Long markerId = ((Number) arr[0]).longValue();
                    Integer cnt = ((Number) arr[1]).intValue();

                    MapMarker marker = markerMap.get(markerId);
                    CampusFacility facility = marker != null ? facilityMap.get(marker.getFacilityId()) : null;

                    // 按类型过滤
                    if (facilityType != null && (facility == null || !facilityType.equals(facility.getFacilityType()))) {
                        continue;
                    }

                    MarkerSummaryItem item = new MarkerSummaryItem();
                    item.setRank(rank++);
                    item.setMarkerId(markerId);
                    item.setMarkerName(facility != null ? facility.getFacilityName() : "");
                    item.setFacilityType(facility != null ? facility.getFacilityType() : null);
                    item.setFacilityTypeName(getFacilityTypeName(facility != null ? facility.getFacilityType() : null));
                    item.setNavigationCount(cnt);
                    item.setViewCount(cnt); // 暂无独立访问数据，暂用导航次数代替
                    if (marker != null) {
                        item.setLongitude(facility != null ? facility.getLongitude() : null);
                        item.setLatitude(facility != null ? facility.getLatitude() : null);
                    }
                    items.add(item);

                    if (items.size() >= effectiveLimit) break;
                }
            }
        }
        return items;
    }

    @Override
    public MarkerVisitResponse getMarkerVisit(Long markerId, String date) {
        MapMarker marker = mapMarkerRepository.findById(markerId)
                .orElseThrow(() -> new BusinessException(404, "标记不存在"));
        CampusFacility facility = facilityRepository.findById(marker.getFacilityId()).orElse(null);

        Integer totalVisits = navigationLogRepository.countByToMarkerId(markerId);
        Integer todayVisits = navigationLogRepository.countTodayVisitsByToMarkerId(
                markerId, LocalDate.now().atStartOfDay());

        // 小时分布：仅当日有数据才查询
        List<TimeSeriesItem> hourly = Collections.emptyList();
        List<Object[]> hourRows = navigationLogRepository.findHourlyDistributionByToMarkerId(markerId);
        if (hourRows != null && !hourRows.isEmpty()) {
            hourly = new ArrayList<>();
            for (Object[] row : hourRows) {
                Integer hour = ((Number) row[0]).intValue();
                Integer cnt = ((Number) row[1]).intValue();
                hourly.add(new TimeSeriesItem(hour + ":00", null, cnt));
            }
        }

        MarkerVisitResponse resp = new MarkerVisitResponse();
        resp.setMarkerId(markerId);
        resp.setMarkerName(facility != null ? facility.getFacilityName() : "");
        resp.setTodayVisits(todayVisits != null ? todayVisits : 0);
        resp.setTotalVisits(totalVisits != null ? totalVisits : 0);
        resp.setHourlyDistribution(hourly);
        return resp;
    }

    private Map<Long, MapMarker> buildMarkerMap() {
        List<MapMarker> markers = mapMarkerRepository.findAll();
        Map<Long, MapMarker> map = new HashMap<>();
        for (MapMarker m : markers) {
            map.put(m.getId(), m);
        }
        return map;
    }

    private Map<Long, CampusFacility> buildFacilityMap(Collection<MapMarker> markers) {
        if (markers.isEmpty()) return Collections.emptyMap();
        List<Long> ids = markers.stream()
                .map(MapMarker::getFacilityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        List<CampusFacility> facilities = facilityRepository.findByIdIn(ids);
        Map<Long, CampusFacility> map = new HashMap<>();
        for (CampusFacility f : facilities) {
            map.put(f.getId(), f);
        }
        return map;
    }

    private List<MarkerSummaryItem> buildPopularDestinations(int limit) {
        Object[] results = navigationLogRepository.findTopDestinations(limit);
        List<MarkerSummaryItem> items = new ArrayList<>();
        if (results != null) {
            Map<Long, MapMarker> markerMap = buildMarkerMap();
            Map<Long, CampusFacility> facilityMap = buildFacilityMap(markerMap.values());

            for (Object row : results) {
                if (row instanceof Object[]) {
                    Object[] arr = (Object[]) row;
                    Long markerId = ((Number) arr[0]).longValue();
                    Integer cnt = ((Number) arr[1]).intValue();

                    MapMarker marker = markerMap.get(markerId);
                    CampusFacility facility = marker != null ? facilityMap.get(marker.getFacilityId()) : null;

                    MarkerSummaryItem item = new MarkerSummaryItem();
                    item.setMarkerId(markerId);
                    item.setMarkerName(facility != null ? facility.getFacilityName() : "");
                    item.setFacilityType(facility != null ? facility.getFacilityType() : null);
                    item.setFacilityTypeName(getFacilityTypeName(facility != null ? facility.getFacilityType() : null));
                    item.setNavigationCount(cnt);
                    items.add(item);
                }
            }
        }
        return items;
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

