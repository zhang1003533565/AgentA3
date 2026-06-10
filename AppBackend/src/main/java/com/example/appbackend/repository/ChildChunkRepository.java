package com.example.appbackend.repository;

import com.example.appbackend.entity.ChildChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildChunkRepository extends JpaRepository<ChildChunk, Long> {

    List<ChildChunk> findBySegmentIdOrderByPositionAsc(Long segmentId);

    List<ChildChunk> findByDocumentIdOrderByPositionAsc(Long documentId);

    long countBySegmentId(Long segmentId);

    long countByDocumentId(Long documentId);
}
