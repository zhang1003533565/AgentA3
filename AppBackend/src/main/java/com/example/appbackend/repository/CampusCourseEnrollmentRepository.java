package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CampusCourseEnrollmentRepository extends JpaRepository<CampusCourseEnrollment, Long> {
    List<CampusCourseEnrollment> findByUserIdOrderByEnrolledTimeDesc(Long userId);
    Optional<CampusCourseEnrollment> findByUserIdAndCourseId(Long userId, Long courseId);
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
    void deleteByUserIdAndCourseId(Long userId, Long courseId);
}
