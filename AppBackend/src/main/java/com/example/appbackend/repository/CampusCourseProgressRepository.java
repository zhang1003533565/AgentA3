package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourseProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampusCourseProgressRepository extends JpaRepository<CampusCourseProgress, Long> {
    List<CampusCourseProgress> findByCourseIdAndUserId(Long courseId, Long userId);
    Optional<CampusCourseProgress> findByCourseIdAndChapterIdAndUserId(Long courseId, Long chapterId, Long userId);
    void deleteByCourseId(Long courseId);
    void deleteByChapterId(Long chapterId);
}
