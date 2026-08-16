package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.MapFloorPlan;
import com.example.appbackend.entity.MapPlaceIndoorPosition;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MapPlaceRepository;
import com.example.appbackend.service.MapPlaceService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(MapPlaceServiceImpl.class)
@ActiveProfiles("test")
class MapPlaceServiceImplTest {

    @Autowired
    private MapPlaceService service;

    @Autowired
    private MapPlaceRepository placeRepository;

    @Test
    void createsTreeFloorPlanAndIndoorPosition() {
        MapPlaceRequest buildingRequest = place("TEACHING", "TEACHING_BUILDING", "第一教学楼", null);
        MapPlaceResponse building = service.create(buildingRequest);

        MapPlaceResponse floor = service.create(place("TEACHING", "FLOOR", "一层", building.getId()));
        MapPlaceResponse classroom = service.create(place("TEACHING", "CLASSROOM", "101教室", floor.getId()));

        MapFloorPlanRequest planRequest = new MapFloorPlanRequest();
        planRequest.setImageUrl("https://example.test/floor-1.png");
        MapFloorPlan plan = service.saveFloorPlan(floor.getId(), planRequest);

        MapIndoorPositionRequest positionRequest = new MapIndoorPositionRequest();
        positionRequest.setFloorPlanId(plan.getId());
        positionRequest.setXRatio(new BigDecimal("25.5000"));
        positionRequest.setYRatio(new BigDecimal("40.0000"));
        MapPlaceIndoorPosition position = service.savePosition(classroom.getId(), positionRequest);

        assertEquals(1, service.tree("TEACHING").size());
        assertEquals("一层", service.tree("TEACHING").getFirst().getChildren().getFirst().getName());
        assertEquals(new BigDecimal("25.5000"), position.getXRatio());
        assertEquals(plan.getId(), service.detail(classroom.getId()).getIndoorPosition().getFloorPlanId());
    }

    @Test
    void rejectsInvalidHierarchyAndPartialCoordinates() {
        MapPlaceResponse sports = service.create(place("SPORTS", "SPORTS_GROUND", "第一运动场", null));

        MapPlaceRequest invalidChild = place("SPORTS", "CLASSROOM", "错误教室", sports.getId());
        assertThrows(BusinessException.class, () -> service.create(invalidChild));

        MapPlaceRequest partialCoordinate = place("SPORTS", "FOOTBALL_FIELD", "足球场", sports.getId());
        partialCoordinate.setLongitude(new BigDecimal("114.1234567"));
        assertThrows(BusinessException.class, () -> service.create(partialCoordinate));
    }

    @Test
    void countsCanteenStallsBelowItsFloors() throws Exception {
        MapPlaceRequest canteenRequest = place("CANTEEN", "CANTEEN", "学一食堂", null);
        canteenRequest.setDescription("食堂详情介绍");
        MapPlaceResponse canteen = service.create(canteenRequest);
        MapPlaceResponse firstFloor = service.create(place("CANTEEN", "FLOOR", "一层", canteen.getId()));
        MapPlaceResponse secondFloor = service.create(place("CANTEEN", "FLOOR", "二层", canteen.getId()));
        service.create(place("CANTEEN", "CANTEEN_STALL", "面食档口", firstFloor.getId()));
        service.create(place("CANTEEN", "CANTEEN_STALL", "快餐档口", firstFloor.getId()));
        service.create(place("CANTEEN", "CANTEEN_STALL", "饮品档口", secondFloor.getId()));

        assertEquals(3L, placeRepository.countCanteenStalls(canteen.getId()));
        List<MapPlaceResponse> structure = service.canteenStructure(canteen.getId());
        assertEquals(5, structure.size());
        assertEquals(2, structure.stream().filter(item -> "FLOOR".equals(item.getPlaceType())).count());
        assertEquals(3, structure.stream().filter(item -> "CANTEEN_STALL".equals(item.getPlaceType())).count());
        MapPlaceResponse listItem = service.list("CANTEEN", null, "CANTEEN", null, null).getFirst();
        assertEquals(3L, listItem.getStallCount());
        assertNull(listItem.getDescription());
        assertFalse(JsonMapper.builder().findAndAddModules().build()
                .writeValueAsString(listItem).contains("\"description\""));
        assertEquals("食堂详情介绍", service.detail(canteen.getId()).getDescription());
    }

    private MapPlaceRequest place(String scene, String type, String name, Long parentId) {
        MapPlaceRequest request = new MapPlaceRequest();
        request.setSceneType(scene);
        request.setPlaceType(type);
        request.setName(name);
        request.setParentId(parentId);
        request.setStatus("ENABLED");
        request.setMapVisible(!"FLOOR".equals(type));
        request.setSortOrder(0);
        return request;
    }
}
