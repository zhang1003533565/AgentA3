package com.example.appbackend.repository;

import com.example.appbackend.entity.StudySubtask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StudySubtaskRepository extends JpaRepository<StudySubtask, Long> {

    List<StudySubtask> findByTaskIdOrderByOrderNumAscIdAsc(Long taskId);

    List<StudySubtask> findByTaskIdInOrderByTaskIdAscOrderNumAscIdAsc(Iterable<Long> taskIds);

    void deleteByTaskIdIn(Collection<Long> taskIds);
}
