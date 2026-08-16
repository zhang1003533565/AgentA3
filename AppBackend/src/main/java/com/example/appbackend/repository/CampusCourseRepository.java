package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampusCourseRepository extends JpaRepository<CampusCourse, Long> {
    List<CampusCourse> findAllByOrderBySortOrderAscUpdateTimeDesc();
    List<CampusCourse> findByPublishStatusOrderBySortOrderAscPublishTimeDesc(String publishStatus);
}
