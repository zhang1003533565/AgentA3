package com.example.appbackend.repository;

import com.example.appbackend.entity.LearningPathItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningPathItemRepository extends JpaRepository<LearningPathItem, Long> {

    List<LearningPathItem> findByPathIdOrderBySequenceNoAscIdAsc(Long pathId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from LearningPathItem item
            where item.pathId = :pathId
            order by item.sequenceNo asc, item.id asc
            """)
    List<LearningPathItem> findByPathIdForUpdate(@Param("pathId") Long pathId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from LearningPathItem item, LearningPath path
            where item.id = :itemId
              and path.id = item.pathId
              and path.userId = :userId
              and path.courseKey = :courseKey
              and path.status = :pathStatus
            """)
    Optional<LearningPathItem> findOwnedActiveByIdForUpdate(
            @Param("itemId") Long itemId,
            @Param("userId") Long userId,
            @Param("courseKey") String courseKey,
            @Param("pathStatus") String pathStatus);
}
