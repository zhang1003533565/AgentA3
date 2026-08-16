package com.example.appbackend.repository;

import com.example.appbackend.entity.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByUserId(Long userId);

    List<CourseSchedule> findByUserIdAndAcademicYearAndSemesterTerm(Long userId, String academicYear, Integer semesterTerm);

    List<CourseSchedule> findByStudentId(String studentId);

    @Modifying
    @Query("DELETE FROM CourseSchedule cs WHERE cs.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM CourseSchedule cs WHERE cs.userId = :userId AND cs.academicYear = :academicYear AND cs.semesterTerm = :semesterTerm")
    void deleteByUserIdAndSemester(@Param("userId") Long userId, @Param("academicYear") String academicYear, @Param("semesterTerm") Integer semesterTerm);

    @Modifying
    @Query("DELETE FROM CourseSchedule cs WHERE cs.studentId = :studentId")
    void deleteByStudentId(@Param("studentId") String studentId);

    long countByUserId(Long userId);

    long countByUserIdAndAcademicYearAndSemesterTerm(Long userId, String academicYear, Integer semesterTerm);
}
