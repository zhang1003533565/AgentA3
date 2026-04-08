package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FacilityRequest;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.MapConfig;
import com.example.appbackend.entity.MapMarker;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.FacilityReviewRepository;
import com.example.appbackend.repository.FavoriteDestinationRepository;
import com.example.appbackend.repository.MapConfigRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.repository.NavigationLogRepository;
import com.example.appbackend.service.FacilityService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class FacilityServiceImpl implements FacilityService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> CONTROL_POINT_TYPE = new TypeReference<>() {};

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

    @Autowired
    private MapConfigRepository mapConfigRepository;

    @Override
    public PageResponse<CampusFacility> getFacilityList(Integer type, String name, Integer status, Integer pageNum, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<CampusFacility> page = facilityRepository.findByConditions(type, name, status, pageRequest);
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
        facility.setImageX(request.getImageX());
        facility.setImageY(request.getImageY());
        facility.setImages(request.getImages());
        facility.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        applyMapImageCoordinates(facility);
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
        if (request.getImageX() != null) facility.setImageX(request.getImageX());
        if (request.getImageY() != null) facility.setImageY(request.getImageY());
        if (request.getImages() != null) facility.setImages(request.getImages());
        if (request.getStatus() != null) facility.setStatus(request.getStatus());
        applyMapImageCoordinates(facility);
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

    private void applyMapImageCoordinates(CampusFacility facility) {
        if (facility.getLongitude() == null || facility.getLatitude() == null) {
          return;
        }

        Point normalized = resolveByControlPoints(facility.getLongitude().doubleValue(), facility.getLatitude().doubleValue());
        if (normalized == null) {
            return;
        }

        facility.setImageX(toDecimal(normalized.x));
        facility.setImageY(toDecimal(normalized.y));
    }

    private Point resolveByControlPoints(double longitude, double latitude) {
        try {
            String raw = getConfig("map_control_points");
            if (raw == null || raw.isBlank()) return null;
            List<Map<String, Object>> points = OBJECT_MAPPER.readValue(raw, CONTROL_POINT_TYPE);
            if (points.size() < 3) return null;

            double[][] design = new double[points.size()][3];
            double[] targetX = new double[points.size()];
            double[] targetY = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                Map<String, Object> point = points.get(i);
                design[i][0] = toDouble(point.get("longitude"));
                design[i][1] = toDouble(point.get("latitude"));
                design[i][2] = 1d;
                targetX[i] = toDouble(point.get("imageX"));
                targetY[i] = toDouble(point.get("imageY"));
            }

            double[] xCoeff = solveAffineCoefficients(design, targetX);
            double[] yCoeff = solveAffineCoefficients(design, targetY);
            if (xCoeff == null || yCoeff == null) return null;

            double x = xCoeff[0] * longitude + xCoeff[1] * latitude + xCoeff[2];
            double y = yCoeff[0] * longitude + yCoeff[1] * latitude + yCoeff[2];
            return clampPoint(new Point(x, y));
        } catch (Exception e) {
            return null;
        }
    }

    private double[] solveAffineCoefficients(double[][] design, double[] target) {
        double[][] ata = new double[3][3];
        double[] atb = new double[3];
        for (int i = 0; i < design.length; i++) {
            for (int r = 0; r < 3; r++) {
                atb[r] += design[i][r] * target[i];
                for (int c = 0; c < 3; c++) {
                    ata[r][c] += design[i][r] * design[i][c];
                }
            }
        }
        return solve3x3(ata, atb);
    }

    private double[] solve3x3(double[][] matrix, double[] vector) {
        double[][] a = new double[3][4];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, 3);
            a[i][3] = vector[i];
        }

        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(a[pivot][col]) < 1e-10) return null;
            if (pivot != col) {
                double[] temp = a[pivot];
                a[pivot] = a[col];
                a[col] = temp;
            }
            double factor = a[col][col];
            for (int j = col; j < 4; j++) {
                a[col][j] /= factor;
            }
            for (int row = 0; row < 3; row++) {
                if (row == col) continue;
                double ratio = a[row][col];
                for (int j = col; j < 4; j++) {
                    a[row][j] -= ratio * a[col][j];
                }
            }
        }
        return new double[] { a[0][3], a[1][3], a[2][3] };
    }

    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private double toDouble(Object value) {
        if (value == null) return 0d;
        return Double.parseDouble(String.valueOf(value));
    }

    private Point clampPoint(Point point) {
        return new Point(
                Math.max(0d, Math.min(1d, point.x)),
                Math.max(0d, Math.min(1d, point.y))
        );
    }

    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private String getConfig(String key) {
        return mapConfigRepository.findByConfigKey(key)
                .map(MapConfig::getConfigValue)
                .orElse(null);
    }

    private static class Point {
        final double x;
        final double y;

        private Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
