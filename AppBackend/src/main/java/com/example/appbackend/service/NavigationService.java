package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.math.BigDecimal;

public interface NavigationService {

    NavigationResponse startNavigation(NavigationRequest request, Long userId);

    NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                     BigDecimal toLongitude, BigDecimal toLatitude, String mode);

    void arriveConfirm(Long navigationId);

    void cancelNavigation(Long navigationId);

    PageResponse<NavigationHistoryItem> getNavigationHistory(Long userId, Integer pageNum, Integer pageSize);
}
