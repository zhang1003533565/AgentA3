package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.FacilityReview;

import java.math.BigDecimal;
import java.util.List;

public interface NavigationService {

    NavigationResponse startNavigation(NavigationRequest request, Long userId);

    NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                     BigDecimal toLongitude, BigDecimal toLatitude, String mode);

    void arriveConfirm(Long navigationId);

    void cancelNavigation(Long navigationId);

    PageResponse<NavigationHistoryItem> getNavigationHistory(Long userId, Integer pageNum, Integer pageSize);

    List<MarkerSummaryItem> getFrequentDestinations(Long userId, Integer limit);
}
