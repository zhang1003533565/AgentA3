package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourseChapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampusCourseChapterRepository extends JpaRepository<CampusCourseChapter, Long> {
    List<CampusCourseChapter> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);
    long countByCourseId(Long courseId);
    void deleteByCourseId(Long courseId);
}
