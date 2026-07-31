package com.example.appbackend.repository;

import com.example.appbackend.entity.FacilityFloor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilityFloorRepository extends JpaRepository<FacilityFloor, Long> {

    List<FacilityFloor> findByFacilityIdOrderBySortOrderAscIdAsc(Long facilityId);

    Optional<FacilityFloor> findByFacilityIdAndFloorName(Long facilityId, String floorName);

    boolean existsByFacilityIdAndFloorNameAndIdNot(Long facilityId, String floorName, Long id);
}
