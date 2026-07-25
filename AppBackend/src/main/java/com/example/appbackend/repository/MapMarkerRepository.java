package com.example.appbackend.repository;

import com.example.appbackend.entity.MapMarker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MapMarkerRepository extends JpaRepository<MapMarker, Long> {

    @Query("SELECT m FROM MapMarker m WHERE " +
           "(:facilityType IS NULL OR m.facilityId IN " +
           "  (SELECT f.id FROM CampusFacility f WHERE f.facilityType = :facilityType)) " +
           "AND (:keyword IS NULL OR m.facilityId IN " +
           "  (SELECT f.id FROM CampusFacility f WHERE f.facilityName LIKE %:keyword%))")
    Page<MapMarker> findByConditions(
            @Param("facilityType") Integer facilityType,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT m FROM MapMarker m WHERE " +
           "m.facilityId IN (SELECT f.id FROM CampusFacility f WHERE f.facilityType IN :facilityTypes) " +
           "AND (:keyword IS NULL OR m.facilityId IN " +
           "  (SELECT f.id FROM CampusFacility f WHERE f.facilityName LIKE %:keyword%))")
    Page<MapMarker> findByFacilityTypes(
            @Param("facilityTypes") List<Integer> facilityTypes,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT m FROM MapMarker m WHERE m.facilityId = :facilityId")
    Optional<MapMarker> findByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT m FROM MapMarker m WHERE m.facilityId IN :facilityIds")
    List<MapMarker> findByFacilityIdIn(@Param("facilityIds") List<Long> facilityIds);

    boolean existsByFacilityId(Long facilityId);
}
