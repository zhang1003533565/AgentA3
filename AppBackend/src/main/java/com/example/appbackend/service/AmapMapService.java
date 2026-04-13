package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.math.BigDecimal;

public interface AmapMapService {

    NavigationRouteResponse getRoute(BigDecimal fromLongitude, BigDecimal fromLatitude,
                                     BigDecimal toLongitude, BigDecimal toLatitude, String mode);

    ReverseGeocoderResponse reverseGeocode(BigDecimal longitude, BigDecimal latitude);

    GeocoderResponse geocode(String address, String region);

    PlaceSearchResponse searchPlaces(String keyword, String region,
                                     BigDecimal latitude, BigDecimal longitude, Integer radius);
}
