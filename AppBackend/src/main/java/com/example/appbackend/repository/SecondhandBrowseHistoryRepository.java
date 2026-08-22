package com.example.appbackend.repository;

import com.example.appbackend.entity.SecondhandBrowseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecondhandBrowseHistoryRepository extends JpaRepository<SecondhandBrowseHistory, Long> {

    Page<SecondhandBrowseHistory> findByUserIdOrderByBrowseTimeDesc(Long userId, Pageable pageable);

    Optional<SecondhandBrowseHistory> findByUserIdAndItemId(Long userId, Long itemId);

    @Modifying
    @Query("UPDATE SecondhandBrowseHistory h SET h.browseTime = :browseTime WHERE h.userId = :userId AND h.itemId = :itemId")
    void updateBrowseTime(@Param("userId") Long userId, @Param("itemId") Long itemId, @Param("browseTime") java.time.LocalDateTime browseTime);

    @Modifying
    @Query("DELETE FROM SecondhandBrowseHistory h WHERE h.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    List<SecondhandBrowseHistory> findByUserId(Long userId);
}
