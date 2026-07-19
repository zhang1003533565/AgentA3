package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamQuestionAssemblyTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface ExamQuestionAssemblyTaskRepository extends JpaRepository<ExamQuestionAssemblyTask, Long> {

    Optional<ExamQuestionAssemblyTask> findByTaskIdAndUserId(String taskId, Long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    List<ExamQuestionAssemblyTask> findByStatusIn(Collection<String> statuses);
}
