package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.MapMarker;
import com.example.appbackend.entity.NavigationLog;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.repository.NavigationLogRepository;
import com.example.appbackend.service.NavigationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public NavigationResponse startNavigation(NavigationRequest request, Long userId) {
        MapMarker marker = mapMarkerRepository.findById(request.getToMarkerId())
                .orElseThrow(() -> new BusinessException(404, "目标标记不存在"));
        CampusFacility facility = campusFacilityRepository.findById(marker.getFacilityId()).orElse(null);

        NavigationLog log = new NavigationLog();
        log.setUserId(userId);
        log.setFromLongitude(request.getFromLongitude());
        log.setFromLatitude(request.getFromLatitude());
        log.setToMarkerId(request.getToMarkerId());
        log.setDistance(BigDecimal.ZERO);
        log.setDuration(0);
        log.setStatus(1);
        log.setCreateTime(LocalDateTime.now());
        NavigationLog saved = navigationLogRepository.save(log);

        BigDecimal toLng = facility != null ? facility.getLongitude() : BigDecimal.ZERO;
        BigDecimal toLat = facility != null ? facility.getLatitude() : BigDecimal.ZERO;
        double distance = calculateDistance(
                request.getFromLongitude().doubleValue(), request.getFromLatitude().doubleValue(),
                toLng.doubleValue(), toLat.doubleValue());

        NavigationResponse resp = new NavigationResponse();
        resp.setNavigationId(saved.getId());
        resp.setFromLongitude(request.getFromLongitude());
        resp.setFromLatitude(request.getFromLatitude());
        resp.setToMarkerId(request.getToMarkerId());
        resp.setToMarkerName(facility != null ? facility.getFacilityName() : "");
        resp.setToLongitude(toLng);
        resp.setToLatitude(toLat);
        resp.setDistance(distance);
        resp.setEstimatedTime((int) (distance / 1.2));
        resp.setRoutePoints(Arrays.asList(
                new RoutePoint(request.getFromLongitude(), request.getFromLatitude()),
                new RoutePoint(toLng, toLat)
        ));
        return resp;
    }

    @Override
    public NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                              BigDecimal toLongitude, BigDecimal toLatitude, String mode) {
        double distance = calculateDistance(
                fromLongitude.doubleValue(), fromLatitude.doubleValue(),
                toLongitude.doubleValue(), toLatitude.doubleValue());

        NavigationRouteResponse resp = new NavigationRouteResponse();
        resp.setDistance(distance);
        resp.setDuration((int) (distance / 1.2));
        resp.setMode(mode != null ? mode : "walking");
        resp.setSteps(Collections.emptyList());
        resp.setPolyline(Arrays.asList(
                new RoutePoint(fromLongitude, fromLatitude),
                new RoutePoint(toLongitude, toLatitude)
        ));
        return resp;
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

    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
