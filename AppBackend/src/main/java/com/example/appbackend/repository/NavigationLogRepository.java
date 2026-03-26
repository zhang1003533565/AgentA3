package com.example.appbackend.repository;

import com.example.appbackend.entity.NavigationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NavigationLogRepository extends JpaRepository<NavigationLog, Long> {

    Page<NavigationLog> findByUserId(Long userId, Pageable pageable);

    Optional<NavigationLog> findByIdAndUserId(Long id, Long userId);

    void deleteByToMarkerId(Long toMarkerId);

    @Query("SELECT COUNT(n) FROM NavigationLog n")
    Integer countTotal();

    @Query("SELECT COUNT(n) FROM NavigationLog n WHERE n.createTime >= :start")
    Integer countSince(@Param("start") LocalDateTime start);

    @Query("SELECT COUNT(n) FROM NavigationLog n WHERE n.status = :status")
    Integer countByStatus(@Param("status") Integer status);

    @Query("SELECT AVG(n.duration) FROM NavigationLog n WHERE n.status = 2")
    Double avgDuration();

    @Query(value = "SELECT to_marker_id, COUNT(*) as cnt FROM navigation_log " +
           "GROUP BY to_marker_id ORDER BY cnt DESC LIMIT :limit",
           nativeQuery = true)
    Object[] findTopDestinations(@Param("limit") int limit);
}
