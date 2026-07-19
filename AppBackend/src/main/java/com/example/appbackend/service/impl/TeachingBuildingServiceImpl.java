package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ClassroomDTO;
import com.example.appbackend.dto.TeachingBuildingDTO;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.Classroom;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ClassroomRepository;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.service.TeachingBuildingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class TeachingBuildingServiceImpl implements TeachingBuildingService {
    private static final int TEACHING_BUILDING = 3;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FacilityRepository facilityRepository;
    private final ClassroomRepository classroomRepository;

    public TeachingBuildingServiceImpl(FacilityRepository facilityRepository, ClassroomRepository classroomRepository) {
        this.facilityRepository = facilityRepository;
        this.classroomRepository = classroomRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeachingBuildingDTO> listBuildings() {
        return facilityRepository.findByFacilityType(TEACHING_BUILDING).stream()
                .map(facility -> toBuildingDTO(facility, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TeachingBuildingDTO getBuilding(Long facilityId) {
        return toBuildingDTO(requireBuilding(facilityId), true);
    }

    @Override
    public ClassroomDTO createClassroom(Long facilityId, ClassroomDTO request) {
        requireBuilding(facilityId);
        if (classroomRepository.existsByBuildingIdAndRoomNo(facilityId, request.getRoomNo().trim())) {
            throw new BusinessException(400, "该教学楼内教室编号已存在");
        }
        Classroom classroom = new Classroom();
        classroom.setBuildingId(facilityId);
        copyEditableFields(request, classroom);
        return toClassroomDTO(classroomRepository.save(classroom));
    }

    @Override
    public ClassroomDTO updateClassroom(Long classroomId, ClassroomDTO request) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new BusinessException(404, "教室不存在"));
        String nextRoomNo = request.getRoomNo().trim();
        if (!nextRoomNo.equals(classroom.getRoomNo())
                && classroomRepository.existsByBuildingIdAndRoomNo(classroom.getBuildingId(), nextRoomNo)) {
            throw new BusinessException(400, "该教学楼内教室编号已存在");
        }
        copyEditableFields(request, classroom);
        return toClassroomDTO(classroomRepository.save(classroom));
    }

    @Override
    public void deleteClassroom(Long classroomId) {
        if (!classroomRepository.existsById(classroomId)) {
            throw new BusinessException(404, "教室不存在");
        }
        classroomRepository.deleteById(classroomId);
    }

    private CampusFacility requireBuilding(Long facilityId) {
        CampusFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new BusinessException(404, "教学楼不存在"));
        if (!Integer.valueOf(TEACHING_BUILDING).equals(facility.getFacilityType())) {
            throw new BusinessException(400, "该设施不是教学楼");
        }
        return facility;
    }

    private TeachingBuildingDTO toBuildingDTO(CampusFacility facility, boolean includeClassrooms) {
        List<Classroom> classrooms = classroomRepository.findByBuildingIdOrderByFloorNoAscRoomNoAsc(facility.getId());
        TeachingBuildingDTO dto = new TeachingBuildingDTO();
        dto.setId(facility.getId());
        dto.setName(facility.getFacilityName());
        dto.setZone(facility.getLocation());
        dto.setImage(firstImage(facility.getImages()));
        dto.setDescription(facility.getDescription());
        dto.setStatus(facility.getStatus());
        dto.setFloorCount(classrooms.stream().map(Classroom::getFloorNo).distinct().mapToInt(Integer::intValue).max().orElse(0));
        dto.setClassroomCount(classrooms.size());
        dto.setTotalSeatCount(classrooms.stream().mapToInt(item -> valueOrZero(item.getSeatCount())).sum());
        dto.setSmartClassroomCount((int) classrooms.stream().filter(item -> Boolean.TRUE.equals(item.getSmart())).count());
        dto.setActiveClassroomCount((int) classrooms.stream().filter(item -> Integer.valueOf(2).equals(item.getStatus())).count());
        dto.setFreeClassroomCount((int) classrooms.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count());
        if (includeClassrooms) {
            dto.setClassrooms(classrooms.stream().map(this::toClassroomDTO).toList());
        }
        return dto;
    }

    private void copyEditableFields(ClassroomDTO request, Classroom target) {
        target.setRoomNo(request.getRoomNo().trim());
        target.setFloorNo(request.getFloorNo());
        target.setSeatCount(request.getSeatCount());
        target.setSmart(Boolean.TRUE.equals(request.getSmart()));
        target.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        target.setOpenTime(request.getOpenTime());
    }

    private ClassroomDTO toClassroomDTO(Classroom classroom) {
        ClassroomDTO dto = new ClassroomDTO();
        BeanUtils.copyProperties(classroom, dto);
        return dto;
    }

    private int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private String firstImage(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return "";
        try {
            List<String> images = OBJECT_MAPPER.readValue(imagesJson, new TypeReference<>() {});
            return images.isEmpty() ? "" : images.get(0);
        } catch (Exception ignored) {
            return "";
        }
    }
}
