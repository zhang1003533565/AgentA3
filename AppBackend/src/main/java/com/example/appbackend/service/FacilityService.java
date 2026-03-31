package com.example.appbackend.service;

import com.example.appbackend.dto.FacilityRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.CampusFacility;

public interface FacilityService {

    PageResponse<CampusFacility> getFacilityList(Integer type, String name, Integer status, Integer pageNum, Integer pageSize);

    CampusFacility createFacility(FacilityRequest request);

    CampusFacility updateFacility(Long id, FacilityRequest request);

    void deleteFacility(Long id);
}
