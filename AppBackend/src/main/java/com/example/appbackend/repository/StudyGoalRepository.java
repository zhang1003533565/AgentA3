package com.example.appbackend.repository;

import com.example.appbackend.entity.StudyGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudyGoalRepository extends JpaRepository<StudyGoal, Long> {

    Optional<StudyGoal> findByIdAndUserId(Long id, Long userId);
}
