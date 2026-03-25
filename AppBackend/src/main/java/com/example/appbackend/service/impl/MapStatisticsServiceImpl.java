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
        List<MarkerSummaryItem> items = new ArrayList<>();
        for (int i = 1; i <= (limit != null ? limit : 10); i++) {
            MarkerSummaryItem item = new MarkerSummaryItem();
            item.setRank(i);
            item.setMarkerId((long) i);
            item.setMarkerName("设施" + i);
            item.setFacilityType(1);
            item.setFacilityTypeName("餐厅");
            item.setViewCount(1000 - i * 50);
            item.setNavigationCount(500 - i * 20);
            items.add(item);
        }
        return items;
    }

    @Override
    public MarkerVisitResponse getMarkerVisit(Long markerId, String date) {
        MapMarker marker = mapMarkerRepository.findById(markerId)
                .orElseThrow(() -> new BusinessException(404, "标记不存在"));
        CampusFacility facility = facilityRepository.findById(marker.getFacilityId()).orElse(null);

        MarkerVisitResponse resp = new MarkerVisitResponse();
        resp.setMarkerId(markerId);
        resp.setMarkerName(facility != null ? facility.getFacilityName() : "");
        resp.setTodayVisits(0);
        resp.setTotalVisits(0);
        resp.setHourlyDistribution(Collections.emptyList());
        return resp;
    }

    private List<MarkerSummaryItem> buildPopularDestinations(int limit) {
        Object[] results = navigationLogRepository.findTopDestinations(limit);
        List<MarkerSummaryItem> items = new ArrayList<>();
        if (results != null) {
            for (Object row : results) {
                if (row instanceof Object[]) {
                    Object[] arr = (Object[]) row;
                    Long markerId = ((Number) arr[0]).longValue();
                    Integer cnt = ((Number) arr[1]).intValue();
                    MarkerSummaryItem item = new MarkerSummaryItem();
                    item.setMarkerId(markerId);
                    item.setMarkerName("");
                    item.setFacilityType(1);
                    item.setFacilityTypeName("餐厅");
                    item.setNavigationCount(cnt);
                    items.add(item);
                }
            }
        }
        return items;
    }
}
