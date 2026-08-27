package com.example.appbackend.repository;

import com.example.appbackend.entity.StudyTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {

    List<StudyTask> findByGoalIdOrderByOrderNumAscIdAsc(Long goalId);

    List<StudyTask> findByGoalIdAndIsCompletedFalseOrderByOrderNumAscIdAsc(Long goalId);

    List<StudyTask> findByGoalIdAndIsCompletedTrueOrderByOrderNumAscIdAsc(Long goalId);

    long countByGoalId(Long goalId);

    long countByGoalIdAndIsCompletedTrue(Long goalId);
}
