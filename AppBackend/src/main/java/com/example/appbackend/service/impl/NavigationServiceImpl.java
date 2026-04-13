package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.MapMarker;
import com.example.appbackend.entity.NavigationLog;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.repository.NavigationLogRepository;
import com.example.appbackend.service.AmapMapService;
import com.example.appbackend.service.NavigationService;
import com.example.appbackend.service.TencentMapService;
import com.example.appbackend.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class NavigationServiceImpl implements NavigationService {

    @Autowired
    private NavigationLogRepository navigationLogRepository;

    @Autowired
    private MapMarkerRepository mapMarkerRepository;

    @Autowired
    private FacilityRepository campusFacilityRepository;

    @Autowired
    private TencentMapService tencentMapService;

    @Autowired
    private AmapMapService amapMapService;

    @Override
    public NavigationResponse startNavigation(NavigationRequest request, Long userId) {
        MapMarker marker = mapMarkerRepository.findById(request.getToMarkerId())
                .orElseThrow(() -> new BusinessException(404, "目标标记不存在"));
        CampusFacility facility = campusFacilityRepository.findById(marker.getFacilityId()).orElse(null);

        BigDecimal toLng = facility != null ? facility.getLongitude() : BigDecimal.ZERO;
        BigDecimal toLat = facility != null ? facility.getLatitude() : BigDecimal.ZERO;

        // 调用腾讯地图路线规划API获取真实路线
        NavigationRouteResponse routeResp = getPreferredRoute(
                request.getFromLongitude(), request.getFromLatitude(),
                toLng, toLat, "walking");

        // 使用腾讯API返回的真实距离和时间
        double distance = routeResp.getDistance();
        int estimatedTime = routeResp.getDuration();

        NavigationLog log = new NavigationLog();
        log.setUserId(userId);
        log.setFromLongitude(request.getFromLongitude());
        log.setFromLatitude(request.getFromLatitude());
        log.setToMarkerId(request.getToMarkerId());
        log.setDistance(BigDecimal.valueOf(distance));
        log.setDuration(estimatedTime);
        log.setStatus(1);
        log.setCreateTime(LocalDateTime.now());
        NavigationLog saved = navigationLogRepository.save(log);

        NavigationResponse resp = new NavigationResponse();
        resp.setNavigationId(saved.getId());
        resp.setFromLongitude(request.getFromLongitude());
        resp.setFromLatitude(request.getFromLatitude());
        resp.setToMarkerId(request.getToMarkerId());
        resp.setToMarkerName(facility != null ? facility.getFacilityName() : "");
        resp.setToLongitude(toLng);
        resp.setToLatitude(toLat);
        resp.setDistance(distance);
        resp.setEstimatedTime(estimatedTime);
        // 兼容旧版 routePoints
        resp.setRoutePoints(Arrays.asList(
                new RoutePoint(request.getFromLongitude(), request.getFromLatitude()),
                new RoutePoint(toLng, toLat)
        ));
        // 腾讯API真实路线
        resp.setPolyline(routeResp.getPolyline());
        resp.setSteps(routeResp.getSteps());
        return resp;
    }

    @Override
    public NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                            BigDecimal toLongitude, BigDecimal toLatitude, String mode) {
        return getPreferredRoute(fromLongitude, fromLatitude, toLongitude, toLatitude, mode);
    }

    @Override
    public void arriveConfirm(Long navigationId) {
        NavigationLog log = navigationLogRepository.findById(navigationId)
                .orElseThrow(() -> new BusinessException(404, "导航记录不存在"));
        log.setStatus(2);
        log.setArriveTime(LocalDateTime.now());
        navigationLogRepository.save(log);
    }

    @Override
    public void cancelNavigation(Long navigationId) {
        NavigationLog log = navigationLogRepository.findById(navigationId)
                .orElseThrow(() -> new BusinessException(404, "导航记录不存在"));
        log.setStatus(3);
        navigationLogRepository.save(log);
    }

    @Override
    public PageResponse<NavigationHistoryItem> getNavigationHistory(Long userId, Integer pageNum, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<NavigationLog> page = navigationLogRepository.findByUserId(userId, pageRequest);
        List<NavigationHistoryItem> items = page.getContent().stream().map(this::toHistoryItem).collect(Collectors.toList());
        return new PageResponse<>(items, page.getTotalElements(), pageNum, pageSize);
    }


    private NavigationHistoryItem toHistoryItem(NavigationLog log) {
        NavigationHistoryItem item = new NavigationHistoryItem();
        item.setId(log.getId());
        item.setUserId(log.getUserId());
        item.setFromLongitude(log.getFromLongitude());
        item.setFromLatitude(log.getFromLatitude());
        item.setToMarkerId(log.getToMarkerId());
        item.setToLongitude(BigDecimal.ZERO);
        item.setToLatitude(BigDecimal.ZERO);
        item.setDistance(log.getDistance() != null ? log.getDistance().doubleValue() : 0.0);
        item.setDuration(log.getDuration());
        item.setStatus(log.getStatus());
        item.setArriveTime(log.getArriveTime());
        item.setCreateTime(log.getCreateTime());

        mapMarkerRepository.findById(log.getToMarkerId())
                .ifPresent(marker -> {
                    CampusFacility f = campusFacilityRepository.findById(marker.getFacilityId()).orElse(null);
                    if (f != null) {
                        item.setToMarkerName(f.getFacilityName());
                        item.setToLongitude(f.getLongitude());
                        item.setToLatitude(f.getLatitude());
                    }
                });
        return item;
    }

    @Override
    public ReverseGeocoderResponse reverseGeocode(BigDecimal longitude, BigDecimal latitude) {
        try {
            return amapMapService.reverseGeocode(longitude, latitude);
        } catch (BusinessException error) {
            return tencentMapService.reverseGeocode(longitude, latitude);
        }
    }

    @Override
    public GeocoderResponse geocode(String address, String region) {
        try {
            return amapMapService.geocode(address, region);
        } catch (BusinessException error) {
            return tencentMapService.geocode(address, region);
        }
    }

    @Override
    public PlaceSearchResponse searchPlaces(String keyword, String region,
                                            BigDecimal latitude, BigDecimal longitude, Integer radius) {
        try {
            return amapMapService.searchPlaces(keyword, region, latitude, longitude, radius);
        } catch (BusinessException error) {
            return tencentMapService.searchPlaces(keyword, region, latitude, longitude, radius);
        }
    }

    @Override
    public CoordTranslateResponse translateCoords(List<CoordTranslateRequest.CoordPoint> points, Integer fromCoordSys) {
        return tencentMapService.translateCoords(points, fromCoordSys);
    }

    private NavigationRouteResponse getPreferredRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                                      BigDecimal toLongitude, BigDecimal toLatitude, String mode) {
        try {
            return amapMapService.getRoute(fromLongitude, fromLatitude, toLongitude, toLatitude, mode);
        } catch (BusinessException error) {
            return tencentMapService.getRoute(fromLongitude, fromLatitude, toLongitude, toLatitude, mode);
        }
    }
}
