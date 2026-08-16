package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourseExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampusCourseExamRepository extends JpaRepository<CampusCourseExam, Long> {
    List<CampusCourseExam> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);
    Optional<CampusCourseExam> findByCourseIdAndPaperId(Long courseId, Long paperId);
    void deleteByCourseId(Long courseId);
}
