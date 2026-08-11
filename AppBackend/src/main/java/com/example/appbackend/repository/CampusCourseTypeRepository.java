package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampusCourseTypeRepository extends JpaRepository<CampusCourseType, Long> {
    List<CampusCourseType> findAllByOrderBySortOrderAscIdAsc();
    Optional<CampusCourseType> findByTypeCode(String typeCode);
    boolean existsByTypeCode(String typeCode);
    boolean existsByTypeName(String typeName);
    List<CampusCourseType> findByTypeCodeIn(List<String> typeCodes);
}
