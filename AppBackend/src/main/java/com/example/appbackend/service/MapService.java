package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface MapService {

    MapConfigResponse getMapConfig();

    void updateMapConfig(MapConfigUpdateRequest request);

    String getConfigItem(String configKey);

    PageResponse<MarkerResponse> getMarkerList(Integer facilityType, String keyword, Integer pageNum, Integer pageSize);

    MarkerResponse getMarkerDetail(Long id);

    MarkerResponse createMarker(MarkerRequest request);

    MarkerResponse updateMarker(Long id, MarkerRequest request);

    void deleteMarker(Long id);

    List<MarkerResponse> batchCreateMarker(List<Long> facilityIds);

    List<MarkerIconInfo> getMarkerIcons();

    List<MarkerSummaryItem> searchFacilities(String keyword, Integer facilityType, Integer limit);

    LocateResponse locate(String keyword);

    List<MarkerResponse> getMarkersByType(Integer facilityType);

    NearbyResponse getNearbyList(Double longitude, Double latitude, Double radius, Integer facilityType, Integer limit, String sortBy);

    NearbyCountResponse getNearbyCount(Double longitude, Double latitude, Double radius);

    MarkerResponse createBuildingMarker(BuildingMarkerRequest request);
}
