package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.MapPlaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MapPlaceServiceImpl implements MapPlaceService {

    private static final Set<String> SCENES = Set.of("CANTEEN", "SPORTS", "TEACHING", "DORMITORY");
    private static final Set<String> ROOT_TYPES = Set.of(
            "CANTEEN", "SPORTS_GROUND", "TEACHING_BUILDING", "DORMITORY_BUILDING"
    );
    private static final Map<String, Set<String>> ALLOWED_CHILDREN = Map.ofEntries(
            Map.entry("CANTEEN", Set.of("FLOOR")),
            Map.entry("TEACHING_BUILDING", Set.of("FLOOR")),
            Map.entry("DORMITORY_BUILDING", Set.of("FLOOR")),
            Map.entry("FLOOR", Set.of(
                    "CANTEEN_STALL", "DINING_AREA", "CLASSROOM", "LABORATORY",
                    "OFFICE", "DORMITORY_ROOM"
            )),
            Map.entry("SPORTS_GROUND", Set.of(
                    "RUNNING_TRACK", "FOOTBALL_FIELD", "BASKETBALL_COURT",
                    "VOLLEYBALL_COURT", "BADMINTON_COURT", "LONG_JUMP_AREA",
                    "SHOT_PUT_AREA", "PLATFORM"
            ))
    );
    private static final Map<String, String> ROOT_SCENE = Map.of(
            "CANTEEN", "CANTEEN",
            "SPORTS_GROUND", "SPORTS",
            "TEACHING_BUILDING", "TEACHING",
            "DORMITORY_BUILDING", "DORMITORY"
    );
    private static final Map<String, Set<String>> FLOOR_CHILDREN_BY_SCENE = Map.of(
            "CANTEEN", Set.of("CANTEEN_STALL", "DINING_AREA"),
            "TEACHING", Set.of("CLASSROOM", "LABORATORY", "OFFICE"),
            "DORMITORY", Set.of("DORMITORY_ROOM")
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MapPlaceRepository placeRepository;
    private final MapPlaceImageRepository imageRepository;
    private final MapPlaceFenceRepository fenceRepository;
    private final MapFloorPlanRepository floorPlanRepository;
    private final MapPlaceIndoorPositionRepository positionRepository;

    public MapPlaceServiceImpl(
            MapPlaceRepository placeRepository,
            MapPlaceImageRepository imageRepository,
            MapPlaceFenceRepository fenceRepository,
            MapFloorPlanRepository floorPlanRepository,
            MapPlaceIndoorPositionRepository positionRepository
    ) {
        this.placeRepository = placeRepository;
        this.imageRepository = imageRepository;
        this.fenceRepository = fenceRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapPlaceResponse> list(
            String sceneType, Long parentId, String placeType, String status, String keyword
    ) {
        String scene = normalizeOptional(sceneType);
        List<MapPlace> source = scene == null
                ? placeRepository.findAll()
                : placeRepository.findBySceneTypeOrderBySortOrderAscIdAsc(scene);
        String type = normalizeOptional(placeType);
        String normalizedStatus = normalizeOptional(status);
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(item -> parentId == null || Objects.equals(parentId, item.getParentId()))
                .filter(item -> type == null || type.equals(item.getPlaceType()))
                .filter(item -> normalizedStatus == null || normalizedStatus.equals(item.getStatus()))
                .filter(item -> normalizedKeyword == null || normalizedKeyword.isBlank()
                        || item.getName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(MapPlace::getSortOrder).thenComparing(MapPlace::getId))
                .map(item -> toResponse(item, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapPlaceResponse> tree(String sceneType) {
        String scene = normalizeRequired(sceneType, "场景类型不能为空");
        requireScene(scene);
        List<MapPlace> places = placeRepository.findBySceneTypeOrderBySortOrderAscIdAsc(scene);
        Map<Long, MapPlaceResponse> responseById = places.stream()
                .map(item -> toResponse(item, false))
                .collect(Collectors.toMap(MapPlaceResponse::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<MapPlaceResponse> roots = new ArrayList<>();
        for (MapPlaceResponse response : responseById.values()) {
            if (response.getParentId() == null) {
                roots.add(response);
                continue;
            }
            MapPlaceResponse parent = responseById.get(response.getParentId());
            if (parent != null) {
                parent.getChildren().add(response);
            }
        }
        return roots;
    }

    @Override
    @Transactional(readOnly = true)
    public MapPlaceResponse detail(Long id) {
        return toResponse(requirePlace(id), true);
    }

    @Override
    public MapPlaceResponse create(MapPlaceRequest request) {
        MapPlace place = new MapPlace();
        apply(place, request, true);
        validateHierarchy(place, null);
        return toResponse(placeRepository.save(place), true);
    }

    @Override
    public MapPlaceResponse update(Long id, MapPlaceRequest request) {
        MapPlace place = requirePlace(id);
        Long previousParentId = place.getParentId();
        apply(place, request, false);
        validateHierarchy(place, id);
        if (!Objects.equals(previousParentId, place.getParentId())) {
            ensureNoCycle(id, place.getParentId());
        }
        return toResponse(placeRepository.save(place), true);
    }

    @Override
    public void delete(Long id) {
        MapPlace place = requirePlace(id);
        if (placeRepository.existsByParentId(id)) {
            throw new BusinessException(400, "该点位存在下级，请先删除或移动下级点位");
        }
        imageRepository.deleteByPlaceId(id);
        fenceRepository.deleteByPlaceId(id);
        positionRepository.deleteByPlaceId(id);
        if ("FLOOR".equals(place.getPlaceType())) {
            floorPlanRepository.findByFloorPlaceId(id).ifPresent(plan -> {
                positionRepository.deleteByFloorPlanId(plan.getId());
                floorPlanRepository.delete(plan);
            });
        }
        placeRepository.delete(place);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapPlaceImage> listImages(Long placeId) {
        requirePlace(placeId);
        return imageRepository.findByPlaceIdOrderBySortOrderAscIdAsc(placeId);
    }

    @Override
    public MapPlaceImage addImage(Long placeId, MapPlaceImageRequest request) {
        requirePlace(placeId);
        if (request == null || request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            throw new BusinessException(400, "图片地址不能为空");
        }
        MapPlaceImage image = new MapPlaceImage();
        image.setPlaceId(placeId);
        image.setImageUrl(request.getImageUrl().trim());
        image.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        image.setFocusX(normalizeFocus(request.getFocusX()));
        image.setFocusY(normalizeFocus(request.getFocusY()));
        return imageRepository.save(image);
    }

    @Override
    public MapPlaceImage updateImage(Long imageId, MapPlaceImageRequest request) {
        MapPlaceImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(404, "图片不存在"));
        if (request == null || request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            throw new BusinessException(400, "图片地址不能为空");
        }
        image.setImageUrl(request.getImageUrl().trim());
        image.setSortOrder(request.getSortOrder() == null ? image.getSortOrder() : request.getSortOrder());
        image.setFocusX(normalizeFocus(request.getFocusX()));
        image.setFocusY(normalizeFocus(request.getFocusY()));
        return imageRepository.save(image);
    }

    @Override
    public void deleteImage(Long imageId) {
        if (!imageRepository.existsById(imageId)) {
            throw new BusinessException(404, "图片不存在");
        }
        imageRepository.deleteById(imageId);
    }

    private int normalizeFocus(Integer value) {
        return value == null ? 50 : Math.max(0, Math.min(100, value));
    }

    @Override
    @Transactional(readOnly = true)
    public MapPlaceFence getFence(Long placeId) {
        requirePlace(placeId);
        return fenceRepository.findByPlaceId(placeId).orElse(null);
    }

    @Override
    public MapPlaceFence saveFence(Long placeId, MapPlaceFenceRequest request) {
        requirePlace(placeId);
        String geometryType = normalizeRequired(request == null ? null : request.getGeometryType(), "图形类型不能为空");
        if (!Set.of("POLYGON", "LINESTRING").contains(geometryType)) {
            throw new BusinessException(400, "图形类型只支持 POLYGON 或 LINESTRING");
        }
        String geometryData = request.getGeometryData();
        validateGeoJson(geometryData, geometryType);
        MapPlaceFence fence = fenceRepository.findByPlaceId(placeId).orElseGet(MapPlaceFence::new);
        fence.setPlaceId(placeId);
        fence.setGeometryType(geometryType);
        fence.setGeometryData(geometryData.trim());
        return fenceRepository.save(fence);
    }

    @Override
    public void deleteFence(Long placeId) {
        requirePlace(placeId);
        fenceRepository.deleteByPlaceId(placeId);
    }

    @Override
    @Transactional(readOnly = true)
    public MapFloorPlan getFloorPlan(Long floorPlaceId) {
        requireFloor(floorPlaceId);
        return floorPlanRepository.findByFloorPlaceId(floorPlaceId).orElse(null);
    }

    @Override
    public MapFloorPlan saveFloorPlan(Long floorPlaceId, MapFloorPlanRequest request) {
        requireFloor(floorPlaceId);
        if (request == null || request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            throw new BusinessException(400, "平面图地址不能为空");
        }
        MapFloorPlan plan = floorPlanRepository.findByFloorPlaceId(floorPlaceId).orElseGet(MapFloorPlan::new);
        plan.setFloorPlaceId(floorPlaceId);
        plan.setImageUrl(request.getImageUrl().trim());
        return floorPlanRepository.save(plan);
    }

    @Override
    public void deleteFloorPlan(Long floorPlaceId) {
        requireFloor(floorPlaceId);
        floorPlanRepository.findByFloorPlaceId(floorPlaceId).ifPresent(plan -> {
            positionRepository.deleteByFloorPlanId(plan.getId());
            floorPlanRepository.delete(plan);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapPlaceIndoorPosition> listPositions(Long floorPlanId) {
        requireFloorPlan(floorPlanId);
        return positionRepository.findByFloorPlanIdOrderByIdAsc(floorPlanId);
    }

    @Override
    public MapPlaceIndoorPosition savePosition(Long placeId, MapIndoorPositionRequest request) {
        MapPlace place = requirePlace(placeId);
        if (request == null || request.getFloorPlanId() == null) {
            throw new BusinessException(400, "所属平面图不能为空");
        }
        MapFloorPlan plan = requireFloorPlan(request.getFloorPlanId());
        MapPlace floor = requireFloor(plan.getFloorPlaceId());
        if (!Objects.equals(place.getParentId(), floor.getId())) {
            throw new BusinessException(400, "室内点位必须是该楼层的直接下级");
        }
        validateRatio(request.getXRatio(), "X");
        validateRatio(request.getYRatio(), "Y");
        MapPlaceIndoorPosition position = positionRepository
                .findByPlaceIdAndFloorPlanId(placeId, plan.getId())
                .orElseGet(MapPlaceIndoorPosition::new);
        position.setPlaceId(placeId);
        position.setFloorPlanId(plan.getId());
        position.setXRatio(request.getXRatio());
        position.setYRatio(request.getYRatio());
        return positionRepository.save(position);
    }

    @Override
    public void deletePosition(Long positionId) {
        if (!positionRepository.existsById(positionId)) {
            throw new BusinessException(404, "室内位置不存在");
        }
        positionRepository.deleteById(positionId);
    }

    private void apply(MapPlace place, MapPlaceRequest request, boolean creating) {
        if (request == null) {
            throw new BusinessException(400, "请求数据不能为空");
        }
        place.setParentId(request.getParentId());
        if (creating || request.getSceneType() != null) {
            place.setSceneType(normalizeRequired(request.getSceneType(), "场景类型不能为空"));
        }
        if (creating || request.getPlaceType() != null) {
            place.setPlaceType(normalizeRequired(request.getPlaceType(), "点位类型不能为空"));
        }
        if (creating || request.getName() != null) {
            String name = request.getName() == null ? null : request.getName().trim();
            if (name == null || name.isBlank()) throw new BusinessException(400, "点位名称不能为空");
            place.setName(name);
        }
        if (request.getDescription() != null) place.setDescription(request.getDescription().trim());
        if (creating || request.getStatus() != null) {
            String status = normalizeOptional(request.getStatus());
            place.setStatus(status == null ? "ENABLED" : status);
        }
        place.setLongitude(request.getLongitude());
        place.setLatitude(request.getLatitude());
        validateCoordinatePair(place.getLongitude(), place.getLatitude());
        if (request.getLocationDesc() != null) place.setLocationDesc(request.getLocationDesc().trim());
        if (creating || request.getMapVisible() != null) {
            place.setMapVisible(request.getMapVisible() == null || request.getMapVisible());
        }
        if (creating || request.getSortOrder() != null) {
            place.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        }
    }

    private void validateHierarchy(MapPlace place, Long currentId) {
        requireScene(place.getSceneType());
        if (place.getParentId() == null) {
            if (!ROOT_TYPES.contains(place.getPlaceType())) {
                throw new BusinessException(400, "顶级点位类型不合法");
            }
            if (!place.getSceneType().equals(ROOT_SCENE.get(place.getPlaceType()))) {
                throw new BusinessException(400, "顶级点位类型与场景类型不匹配");
            }
            return;
        }
        if (Objects.equals(currentId, place.getParentId())) {
            throw new BusinessException(400, "不能把自己设为上级");
        }
        MapPlace parent = requirePlace(place.getParentId());
        if (!parent.getSceneType().equals(place.getSceneType())) {
            throw new BusinessException(400, "父子点位必须属于同一场景");
        }
        Set<String> allowed = ALLOWED_CHILDREN.getOrDefault(parent.getPlaceType(), Set.of());
        if (!allowed.contains(place.getPlaceType())) {
            throw new BusinessException(400, "该点位类型不能挂载在当前上级下");
        }
        if ("FLOOR".equals(parent.getPlaceType())
                && !FLOOR_CHILDREN_BY_SCENE.getOrDefault(place.getSceneType(), Set.of()).contains(place.getPlaceType())) {
            throw new BusinessException(400, "楼层下的点位类型与场景不匹配");
        }
    }

    private void ensureNoCycle(Long placeId, Long parentId) {
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (Objects.equals(placeId, cursor)) {
                throw new BusinessException(400, "点位层级不能形成循环");
            }
            cursor = requirePlace(cursor).getParentId();
        }
    }

    private MapPlaceResponse toResponse(MapPlace place, boolean includeDetails) {
        MapPlaceResponse response = new MapPlaceResponse();
        response.setId(place.getId());
        response.setParentId(place.getParentId());
        response.setSceneType(place.getSceneType());
        response.setPlaceType(place.getPlaceType());
        response.setName(place.getName());
        response.setDescription(place.getDescription());
        response.setStatus(place.getStatus());
        response.setLongitude(place.getLongitude());
        response.setLatitude(place.getLatitude());
        response.setLocationDesc(place.getLocationDesc());
        response.setMapVisible(place.getMapVisible());
        response.setSortOrder(place.getSortOrder());
        response.setCreatedAt(place.getCreatedAt());
        response.setUpdatedAt(place.getUpdatedAt());
        response.setImages(imageRepository.findByPlaceIdOrderBySortOrderAscIdAsc(place.getId()));
        if (includeDetails) {
            response.setFence(fenceRepository.findByPlaceId(place.getId()).orElse(null));
            if ("FLOOR".equals(place.getPlaceType())) {
                response.setFloorPlan(floorPlanRepository.findByFloorPlaceId(place.getId()).orElse(null));
            } else if (place.getParentId() != null) {
                floorPlanRepository.findByFloorPlaceId(place.getParentId()).ifPresent(plan ->
                        response.setIndoorPosition(positionRepository
                                .findByPlaceIdAndFloorPlanId(place.getId(), plan.getId()).orElse(null))
                );
            }
        }
        return response;
    }

    private MapPlace requirePlace(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "点位不存在"));
    }

    private MapPlace requireFloor(Long id) {
        MapPlace floor = requirePlace(id);
        if (!"FLOOR".equals(floor.getPlaceType())) {
            throw new BusinessException(400, "指定点位不是楼层");
        }
        return floor;
    }

    private MapFloorPlan requireFloorPlan(Long id) {
        return floorPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "楼层平面图不存在"));
    }

    private void requireScene(String scene) {
        if (!SCENES.contains(scene)) {
            throw new BusinessException(400, "场景类型不合法");
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new BusinessException(400, message);
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCoordinatePair(BigDecimal longitude, BigDecimal latitude) {
        if ((longitude == null) != (latitude == null)) {
            throw new BusinessException(400, "经度和纬度必须同时填写或同时留空");
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException(400, "经度必须在 -180 到 180 之间");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException(400, "纬度必须在 -90 到 90 之间");
        }
    }

    private void validateRatio(BigDecimal value, String axis) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(400, axis + " 百分比必须在 0 到 100 之间");
        }
    }

    private void validateGeoJson(String raw, String geometryType) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(400, "GeoJSON 数据不能为空");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (!node.isObject() || !geometryType.equalsIgnoreCase(node.path("type").asText())
                    || !node.path("coordinates").isArray()) {
                throw new BusinessException(400, "GeoJSON 类型或坐标格式不正确");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(400, "GeoJSON 格式不合法");
        }
    }
}
