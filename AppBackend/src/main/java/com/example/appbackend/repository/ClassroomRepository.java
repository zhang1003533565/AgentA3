package com.example.appbackend.repository;

import com.example.appbackend.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    List<Classroom> findByBuildingIdOrderByFloorNoAscRoomNoAsc(Long buildingId);
    boolean existsByBuildingIdAndRoomNo(Long buildingId, String roomNo);
}
