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
           "AND (:name IS NULL OR f.facilityName LIKE %:name%)")
    Page<CampusFacility> findByConditions(
            @Param("type") Integer type,
            @Param("name") String name,
            Pageable pageable);

    List<CampusFacility> findByFacilityType(Integer facilityType);
}
