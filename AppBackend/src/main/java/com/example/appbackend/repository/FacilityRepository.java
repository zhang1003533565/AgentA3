package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusFacility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacilityRepository extends JpaRepository<CampusFacility, Long> {

    List<CampusFacility> findByIdIn(List<Long> ids);

    @Query("SELECT f FROM CampusFacility f WHERE " +
           "(:type IS NULL OR f.facilityType = :type) " +
           "AND (:name IS NULL OR f.facilityName LIKE %:name%) " +
           "AND (:status IS NULL OR f.status = :status)")
    Page<CampusFacility> findByConditions(
            @Param("type") Integer type,
            @Param("name") String name,
            @Param("status") Integer status,
            Pageable pageable);

    List<CampusFacility> findByFacilityType(Integer facilityType);

    /**
     * 用 bounding-box 预过滤：在以 (lat,lon) 为圆心、半径 meter 的正方形范围内查询设施。
     * 在 bounding-box 内再以 Haversine 精确过滤。
     */
    @Query("SELECT f FROM CampusFacility f WHERE " +
           "(:type IS NULL OR f.facilityType = :type) " +
           "AND f.latitude IS NOT NULL AND f.longitude IS NOT NULL " +
           "AND f.latitude >= :latMin AND f.latitude <= :latMax " +
           "AND f.longitude >= :lonMin AND f.longitude <= :lonMax")
    List<CampusFacility> findNearby(
            @Param("type") Integer type,
            @Param("latMin") Double latMin,
            @Param("latMax") Double latMax,
            @Param("lonMin") Double lonMin,
            @Param("lonMax") Double lonMax);
}
