package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FavoriteItem;
import com.example.appbackend.dto.FavoriteRequest;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.FavoriteDestination;
import com.example.appbackend.entity.MapMarker;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.FavoriteDestinationRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.service.FacilityTypeService;
import com.example.appbackend.service.MapFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MapFavoriteServiceImpl implements MapFavoriteService {

    @Autowired
    private FavoriteDestinationRepository favoriteRepository;

    @Autowired
    private MapMarkerRepository mapMarkerRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private FacilityTypeService facilityTypeService;

    @Override
    public FavoriteItem addFavorite(FavoriteRequest request, Long userId) {
        if (favoriteRepository.existsByUserIdAndMarkerId(userId, request.getMarkerId())) {
            throw new BusinessException(409, "该目的地已收藏");
        }
        MapMarker marker = mapMarkerRepository.findById(request.getMarkerId())
                .orElseThrow(() -> new BusinessException(404, "标记不存在"));
        CampusFacility facility = facilityRepository.findById(marker.getFacilityId()).orElse(null);

        FavoriteDestination favorite = new FavoriteDestination();
        favorite.setUserId(userId);
        favorite.setMarkerId(request.getMarkerId());
        favorite.setMarkerName(facility != null ? facility.getFacilityName() : "");
        favorite.setLongitude(facility != null ? facility.getLongitude() : null);
        favorite.setLatitude(facility != null ? facility.getLatitude() : null);
        favorite.setFacilityType(facility != null ? facility.getFacilityType() : null);
        favorite.setRemark(request.getRemark());
        favorite.setCreateTime(LocalDateTime.now());
        FavoriteDestination saved = favoriteRepository.save(favorite);

        FavoriteItem item = new FavoriteItem();
        item.setId(saved.getId());
        item.setMarkerId(saved.getMarkerId());
        item.setMarkerName(saved.getMarkerName());
        item.setFacilityType(saved.getFacilityType());
        item.setFacilityTypeName(getFacilityTypeName(saved.getFacilityType()));
        item.setLongitude(saved.getLongitude());
        item.setLatitude(saved.getLatitude());
        item.setRemark(saved.getRemark());
        item.setCreateTime(saved.getCreateTime());
        return item;
    }

    @Override
    public List<FavoriteItem> getFavoriteList(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(this::toItem).collect(Collectors.toList());
    }

    @Override
    public void deleteFavorite(Long id, Long userId) {
        FavoriteDestination favorite = favoriteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(404, "收藏记录不存在"));
        favoriteRepository.delete(favorite);
    }

    private FavoriteItem toItem(FavoriteDestination f) {
        FavoriteItem item = new FavoriteItem();
        item.setId(f.getId());
        item.setMarkerId(f.getMarkerId());
        item.setMarkerName(f.getMarkerName());
        item.setFacilityType(f.getFacilityType());
        item.setFacilityTypeName(getFacilityTypeName(f.getFacilityType()));
        item.setLongitude(f.getLongitude());
        item.setLatitude(f.getLatitude());
        item.setRemark(f.getRemark());
        item.setCreateTime(f.getCreateTime());
        return item;
    }

    private String getFacilityTypeName(Integer type) {
        return facilityTypeService.getLabel(type);
    }
}
