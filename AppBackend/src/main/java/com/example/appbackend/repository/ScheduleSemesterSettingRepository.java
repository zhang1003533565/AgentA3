package com.example.appbackend.repository;

import com.example.appbackend.entity.ScheduleSemesterSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleSemesterSettingRepository extends JpaRepository<ScheduleSemesterSetting, Long> {

    Optional<ScheduleSemesterSetting> findByUserIdAndAcademicYearAndSemesterTerm(Long userId, String academicYear, Integer semesterTerm);

    Optional<ScheduleSemesterSetting> findFirstByUserIdAndSelectedFlagTrue(Long userId);

    List<ScheduleSemesterSetting> findByUserIdOrderByAcademicYearDescSemesterTermDesc(Long userId);

    @Modifying
    @Query("UPDATE ScheduleSemesterSetting s SET s.selectedFlag = false WHERE s.userId = :userId")
    void clearSelectedByUserId(@Param("userId") Long userId);
}
