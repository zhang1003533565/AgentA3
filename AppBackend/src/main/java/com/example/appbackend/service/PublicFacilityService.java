package com.example.appbackend.service;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.PublicFacilityRequest;
import com.example.appbackend.entity.PublicFacility;
import com.example.appbackend.repository.PublicFacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicFacilityService {

    private final PublicFacilityRepository publicFacilityRepository;

    public Page<PublicFacility> getFacilities(String type, String keyword, String sortBy, int page, int size) {
        Sort sort = resolveSort(sortBy);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, sort);

        boolean hasType = type != null && !type.trim().isEmpty() && !"ALL".equalsIgnoreCase(type.trim());
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();

        if (hasType && hasKeyword) {
            return publicFacilityRepository.findByTypeAndNameContaining(type.trim(), keyword.trim(), pageable);
        } else if (hasType) {
            return publicFacilityRepository.findByType(type.trim(), pageable);
        } else if (hasKeyword) {
            return publicFacilityRepository.findByNameContaining(keyword.trim(), pageable);
        } else {
            return publicFacilityRepository.findAll(pageable);
        }
    }

    public PageResponse<PublicFacility> getFacilitiesPage(String type, String keyword, String sortBy, Integer page, Integer size) {
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? size : 10;
        Page<PublicFacility> result = getFacilities(type, keyword, sortBy, pageNum, pageSize);
        return new PageResponse<>(
            result.getContent(),
            result.getTotalElements(),
            pageNum,
            pageSize
        );
    }

    public PublicFacility getFacility(Long id) {
        return publicFacilityRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("设施不存在: " + id));
    }

    public List<PublicFacility> getFacilitiesByType(String type) {
        if (type == null || type.trim().isEmpty() || "ALL".equalsIgnoreCase(type.trim())) {
            return publicFacilityRepository.findAll();
        }
        return publicFacilityRepository.findAllByType(type.trim());
    }

    @Transactional
    public PublicFacility createFacility(PublicFacilityRequest request) {
        PublicFacility facility = new PublicFacility();
        facility.setName(request.getName());
        facility.setType(request.getType());
        facility.setLocation(request.getLocation());
        facility.setDescription(request.getDescription());
        facility.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        facility.setLatitude(request.getLatitude());
        facility.setLongitude(request.getLongitude());
        facility.setDistance(request.getDistance());
        facility.setImageUrl(request.getImageUrl());
        return publicFacilityRepository.save(facility);
    }

    @Transactional
    public PublicFacility updateFacility(Long id, PublicFacilityRequest request) {
        PublicFacility facility = publicFacilityRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("设施不存在: " + id));

        if (request.getName() != null) facility.setName(request.getName());
        if (request.getType() != null) facility.setType(request.getType());
        if (request.getLocation() != null) facility.setLocation(request.getLocation());
        if (request.getDescription() != null) facility.setDescription(request.getDescription());
        if (request.getStatus() != null) facility.setStatus(request.getStatus());
        if (request.getLatitude() != null) facility.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) facility.setLongitude(request.getLongitude());
        if (request.getDistance() != null) facility.setDistance(request.getDistance());
        if (request.getImageUrl() != null) facility.setImageUrl(request.getImageUrl());

        return publicFacilityRepository.save(facility);
    }

    @Transactional
    public void deleteFacility(Long id) {
        if (!publicFacilityRepository.existsById(id)) {
            throw new IllegalArgumentException("设施不存在: " + id);
        }
        publicFacilityRepository.deleteById(id);
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String trimmed = sortBy.trim();
        boolean ascending = !trimmed.startsWith("-");
        String property = ascending ? trimmed : trimmed.substring(1);
        return Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, property);
    }
}
