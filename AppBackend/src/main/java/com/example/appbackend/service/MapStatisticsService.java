package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.util.List;

public interface MapStatisticsService {

    NavigationStatisticsResponse getNavigationStatistics(String startDate, String endDate);

    List<MarkerSummaryItem> getFacilityHeat(Integer facilityType, String startDate, String endDate, Integer limit);

    MarkerVisitResponse getMarkerVisit(Long markerId, String date);
}
