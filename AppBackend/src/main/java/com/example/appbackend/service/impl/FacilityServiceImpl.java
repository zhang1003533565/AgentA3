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
    private static final TypeReference<List<List<BigDecimal>>> BOUNDARY_POINT_TYPE = new TypeReference<>() {};

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
    public CampusFacility getFacilityById(Long id) {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "设施不存在"));
    }

    @Override
    public CampusFacility createFacility(FacilityRequest request) {
        CampusFacility facility = new CampusFacility();
        facility.setFacilityName(request.getFacilityName());
        facility.setFacilityType(request.getFacilityType());
        facility.setDescription(request.getDescription());
        facility.setMaterial(request.getMaterial());
        facility.setHeight(request.getHeight());
        facility.setWeight(request.getWeight());
        facility.setBaseType(request.getBaseType());
        facility.setCultureBackground(request.getCultureBackground());
        facility.setCultureHighlightText(request.getCultureHighlightText());
        facility.setMeaningInterpretation(request.getMeaningInterpretation());
        facility.setCampusStory(request.getCampusStory());
        facility.setLocation(request.getLocation());
        facility.setLongitude(request.getLongitude());
        facility.setLatitude(request.getLatitude());
        facility.setImageX(request.getImageX());
        facility.setImageY(request.getImageY());
        facility.setImages(request.getImages());
        facility.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        applyGeometry(facility, request);
        applyMapImageCoordinates(facility);
        facility.setCreateTime(LocalDateTime.now());
        facility.setUpdateTime(LocalDateTime.now());
        CampusFacility saved = facilityRepository.save(facility);

        MapMarker marker = new MapMarker();
        marker.setFacilityId(saved.getId());
        marker.setSort(saved.getId() != null ? saved.getId().intValue() : 0);
        marker.setCreateTime(LocalDateTime.now());
        marker.setUpdateTime(LocalDateTime.now());
        mapMarkerRepository.save(marker);

        return saved;
    }

    @Override
    public CampusFacility updateFacility(Long id, FacilityRequest request) {
        CampusFacility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "设施不存在"));
        if (request.getFacilityName() != null) facility.setFacilityName(request.getFacilityName());
        if (request.getFacilityType() != null) facility.setFacilityType(request.getFacilityType());
        if (request.getDescription() != null) facility.setDescription(request.getDescription());
        if (request.getMaterial() != null) facility.setMaterial(request.getMaterial());
        if (request.getHeight() != null) facility.setHeight(request.getHeight());
        if (request.getWeight() != null) facility.setWeight(request.getWeight());
        if (request.getBaseType() != null) facility.setBaseType(request.getBaseType());
        if (request.getCultureBackground() != null) facility.setCultureBackground(request.getCultureBackground());
        if (request.getCultureHighlightText() != null) facility.setCultureHighlightText(request.getCultureHighlightText());
        if (request.getMeaningInterpretation() != null) facility.setMeaningInterpretation(request.getMeaningInterpretation());
        if (request.getCampusStory() != null) facility.setCampusStory(request.getCampusStory());
        if (request.getLocation() != null) facility.setLocation(request.getLocation());
        if (request.getLongitude() != null) facility.setLongitude(request.getLongitude());
        if (request.getLatitude() != null) facility.setLatitude(request.getLatitude());
        if (request.getImageX() != null) facility.setImageX(request.getImageX());
        if (request.getImageY() != null) facility.setImageY(request.getImageY());
        if (request.getImages() != null) facility.setImages(request.getImages());
        if (request.getStatus() != null) facility.setStatus(request.getStatus());
        applyGeometry(facility, request);
        applyMapImageCoordinates(facility);
        facility.setUpdateTime(LocalDateTime.now());
        return facilityRepository.save(facility);
    }

    private void applyGeometry(CampusFacility facility, FacilityRequest request) {
        boolean geometryTypeProvided = request.getGeometryType() != null;
        String sourceType = geometryTypeProvided ? request.getGeometryType() : facility.getGeometryType();
        String geometryType = normalizeGeometryType(sourceType);
        facility.setGeometryType(geometryType);

        if ("POINT".equals(geometryType)) {
            if (geometryTypeProvided) {
                facility.setBoundaryPoints(null);
            }
            return;
        }

        String rawBoundary = request.getBoundaryPoints() != null
                ? request.getBoundaryPoints()
                : facility.getBoundaryPoints();
        List<List<BigDecimal>> boundary = parseBoundaryPoints(rawBoundary);
        facility.setBoundaryPoints(writeBoundaryPoints(boundary));

        BigDecimal longitudeSum = BigDecimal.ZERO;
        BigDecimal latitudeSum = BigDecimal.ZERO;
        for (List<BigDecimal> point : boundary) {
            longitudeSum = longitudeSum.add(point.get(0));
            latitudeSum = latitudeSum.add(point.get(1));
        }
        BigDecimal count = BigDecimal.valueOf(boundary.size());
        facility.setLongitude(longitudeSum.divide(count, 14, RoundingMode.HALF_UP));
        facility.setLatitude(latitudeSum.divide(count, 14, RoundingMode.HALF_UP));
    }

    private String normalizeGeometryType(String geometryType) {
        if (geometryType == null || geometryType.isBlank() || "POINT".equalsIgnoreCase(geometryType)) {
            return "POINT";
        }
        if ("AREA".equalsIgnoreCase(geometryType)) {
            return "AREA";
        }
        throw new BusinessException(400, "空间形态仅支持 POINT 或 AREA");
    }

    private List<List<BigDecimal>> parseBoundaryPoints(String rawBoundary) {
        if (rawBoundary == null || rawBoundary.isBlank()) {
            throw new BusinessException(400, "区域围栏至少需要3个坐标点");
        }
        try {
            List<List<BigDecimal>> points = OBJECT_MAPPER.readValue(rawBoundary, BOUNDARY_POINT_TYPE);
            if (points == null || points.size() < 3) {
                throw new BusinessException(400, "区域围栏至少需要3个坐标点");
            }
            for (List<BigDecimal> point : points) {
                if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                    throw new BusinessException(400, "区域围栏坐标格式不正确");
                }
                BigDecimal longitude = point.get(0);
                BigDecimal latitude = point.get(1);
                if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                        || longitude.compareTo(BigDecimal.valueOf(180)) > 0
                        || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                        || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
                    throw new BusinessException(400, "区域围栏坐标超出有效范围");
                }
            }
            return points;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(400, "区域围栏坐标格式不正确");
        }
    }

    private String writeBoundaryPoints(List<List<BigDecimal>> points) {
        try {
            return OBJECT_MAPPER.writeValueAsString(points);
        } catch (Exception exception) {
            throw new BusinessException(400, "区域围栏坐标序列化失败");
        }
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
