package com.example.appbackend.repository;

import com.example.appbackend.entity.CareerChapterProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareerChapterProgressRepository extends JpaRepository<CareerChapterProgress, Long> {
    List<CareerChapterProgress> findByUserIdAndCareerIdAndSkillId(Long userId, String careerId, String skillId);
    Optional<CareerChapterProgress> findByUserIdAndCareerIdAndSkillIdAndCourseIdAndChapterId(
            Long userId, String careerId, String skillId, Long courseId, Long chapterId);
}
