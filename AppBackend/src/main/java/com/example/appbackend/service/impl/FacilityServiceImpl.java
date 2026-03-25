package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FacilityRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.MapMarker;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.FacilityReviewRepository;
import com.example.appbackend.repository.FavoriteDestinationRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.repository.NavigationLogRepository;
import com.example.appbackend.service.FacilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class FacilityServiceImpl implements FacilityService {

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private MapMarkerRepository mapMarkerRepository;

    @Autowired
    private FavoriteDestinationRepository favoriteDestinationRepository;

    @Autowired
    private NavigationLogRepository navigationLogRepository;

    @Autowired
    private FacilityReviewRepository facilityReviewRepository;

    @Override
    public PageResponse<CampusFacility> getFacilityList(Integer type, String name, Integer pageNum, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<CampusFacility> page = facilityRepository.findByConditions(type, name, pageRequest);
        return new PageResponse<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public CampusFacility createFacility(FacilityRequest request) {
        CampusFacility facility = new CampusFacility();
        facility.setFacilityName(request.getFacilityName());
        facility.setFacilityType(request.getFacilityType());
        facility.setDescription(request.getDescription());
        facility.setLocation(request.getLocation());
        facility.setLongitude(request.getLongitude());
        facility.setLatitude(request.getLatitude());
        facility.setImages(request.getImages());
        facility.setCreateTime(LocalDateTime.now());
        facility.setUpdateTime(LocalDateTime.now());
        return facilityRepository.save(facility);
    }

    @Override
    public CampusFacility updateFacility(Long id, FacilityRequest request) {
        CampusFacility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "设施不存在"));
        if (request.getFacilityName() != null) facility.setFacilityName(request.getFacilityName());
        if (request.getFacilityType() != null) facility.setFacilityType(request.getFacilityType());
        if (request.getDescription() != null) facility.setDescription(request.getDescription());
        if (request.getLocation() != null) facility.setLocation(request.getLocation());
        if (request.getLongitude() != null) facility.setLongitude(request.getLongitude());
        if (request.getLatitude() != null) facility.setLatitude(request.getLatitude());
        if (request.getImages() != null) facility.setImages(request.getImages());
        facility.setUpdateTime(LocalDateTime.now());
        return facilityRepository.save(facility);
    }

    @Override
    public void deleteFacility(Long id) {
        if (!facilityRepository.existsById(id)) {
            throw new BusinessException(404, "设施不存在");
        }

        mapMarkerRepository.findByFacilityId(id).ifPresent(marker -> {
            Long markerId = marker.getId();
            favoriteDestinationRepository.deleteByMarkerId(markerId);
            navigationLogRepository.deleteByToMarkerId(markerId);
            mapMarkerRepository.delete(marker);
        });

        facilityReviewRepository.deleteByFacilityId(id);
        facilityRepository.deleteById(id);
    }
}
