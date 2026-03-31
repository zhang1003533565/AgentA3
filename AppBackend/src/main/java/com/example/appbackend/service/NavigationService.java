package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface NavigationService {

    NavigationResponse startNavigation(NavigationRequest request, Long userId);

    NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                     BigDecimal toLongitude, BigDecimal toLatitude, String mode);

    void arriveConfirm(Long navigationId);

    void cancelNavigation(Long navigationId);

    PageResponse<NavigationHistoryItem> getNavigationHistory(Long userId, Integer pageNum, Integer pageSize);

    ReverseGeocoderResponse reverseGeocode(BigDecimal longitude, BigDecimal latitude);

    GeocoderResponse geocode(String address, String region);

    PlaceSearchResponse searchPlaces(String keyword, String region,
                                     BigDecimal latitude, BigDecimal longitude, Integer radius);

    CoordTranslateResponse translateCoords(List<CoordTranslateRequest.CoordPoint> points, Integer fromCoordSys);
}
