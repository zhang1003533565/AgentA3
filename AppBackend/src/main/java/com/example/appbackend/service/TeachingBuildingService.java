package com.example.appbackend.service;

import com.example.appbackend.dto.ClassroomDTO;
import com.example.appbackend.dto.TeachingBuildingDTO;

import java.util.List;

public interface TeachingBuildingService {
    List<TeachingBuildingDTO> listBuildings();
    TeachingBuildingDTO getBuilding(Long facilityId);
    ClassroomDTO createClassroom(Long facilityId, ClassroomDTO request);
    ClassroomDTO updateClassroom(Long classroomId, ClassroomDTO request);
    void deleteClassroom(Long classroomId);
}
