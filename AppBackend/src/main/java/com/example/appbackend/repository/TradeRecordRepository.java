package com.example.appbackend.repository;

import com.example.appbackend.entity.TradeRecord;
import com.example.appbackend.entity.TradeRecord.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<TradeRecord> findByItemIdAndBuyerId(Long itemId, Long buyerId);

    List<TradeRecord> findByItemIdAndStatusIn(Long itemId, Collection<TradeStatus> statuses);

    Optional<TradeRecord> findByItemIdAndBuyerIdAndStatusIn(Long itemId, Long buyerId, Collection<TradeStatus> statuses);
}
