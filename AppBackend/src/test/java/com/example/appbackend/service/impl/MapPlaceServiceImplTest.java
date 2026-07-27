package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.MapFloorPlan;
import com.example.appbackend.entity.MapPlaceIndoorPosition;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MapPlaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(MapPlaceServiceImpl.class)
@ActiveProfiles("test")
class MapPlaceServiceImplTest {

    @Autowired
    private MapPlaceService service;

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
