package com.example.appbackend.repository;

import com.example.appbackend.entity.TradeRecord;
import com.example.appbackend.entity.TradeRecord.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {

    @Query("SELECT tr FROM TradeRecord tr WHERE tr.buyerId = :userId OR tr.sellerId = :userId ORDER BY tr.updateTime DESC")
    Page<TradeRecord> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT tr FROM TradeRecord tr WHERE (tr.buyerId = :userId OR tr.sellerId = :userId) AND tr.itemId = :itemId ORDER BY tr.updateTime DESC")
    List<TradeRecord> findByUserIdAndItemId(@Param("userId") Long userId, @Param("itemId") Long itemId);

    @Query("SELECT tr FROM TradeRecord tr WHERE tr.buyerId = :userId ORDER BY tr.updateTime DESC")
    Page<TradeRecord> findByBuyerId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT tr FROM TradeRecord tr WHERE tr.sellerId = :userId ORDER BY tr.updateTime DESC")
    Page<TradeRecord> findBySellerId(@Param("userId") Long userId, Pageable pageable);

    Optional<TradeRecord> findByItemIdAndBuyerId(Long itemId, Long buyerId);

    List<TradeRecord> findByItemIdAndStatusIn(Long itemId, Collection<TradeStatus> statuses);

    Optional<TradeRecord> findByItemIdAndBuyerIdAndStatusIn(Long itemId, Long buyerId, Collection<TradeStatus> statuses);

    @Modifying
    @Query("DELETE FROM TradeRecord tr WHERE tr.itemId = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);
}
