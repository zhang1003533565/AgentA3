package com.example.appbackend.repository;

import com.example.appbackend.entity.ChatSession;
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
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByItemId(Long itemId);

    Optional<ChatSession> findByItemIdAndBuyerId(Long itemId, Long buyerId);

    @Query("SELECT cs FROM ChatSession cs WHERE cs.buyerId = :userId OR cs.sellerId = :userId ORDER BY cs.lastTime DESC")
    Page<ChatSession> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.sessionId = :sessionId AND cm.senderId != :userId AND cm.isRead = false")
    int countUnreadBySessionAndUser(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(CASE WHEN cs.buyerId = :userId THEN cs.buyerUnreadCount ELSE cs.sellerUnreadCount END), 0) " +
           "FROM ChatSession cs WHERE cs.buyerId = :userId OR cs.sellerId = :userId")
    long sumUnreadByUser(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE ChatSession cs SET cs.sellerUnreadCount = cs.sellerUnreadCount + 1, " +
           "cs.lastMessage = :lastMessage, cs.lastTime = :lastTime WHERE cs.id = :sessionId")
    void incrementSellerUnreadAndUpdateLast(@Param("sessionId") Long sessionId,
            @Param("lastMessage") String lastMessage, @Param("lastTime") java.time.LocalDateTime lastTime);

    @Modifying
    @Query("UPDATE ChatSession cs SET cs.buyerUnreadCount = cs.buyerUnreadCount + 1, " +
           "cs.lastMessage = :lastMessage, cs.lastTime = :lastTime WHERE cs.id = :sessionId")
    void incrementBuyerUnreadAndUpdateLast(@Param("sessionId") Long sessionId,
            @Param("lastMessage") String lastMessage, @Param("lastTime") java.time.LocalDateTime lastTime);

    @Modifying
    @Query("UPDATE ChatSession cs SET cs.sellerUnreadCount = 0 WHERE cs.id = :sessionId")
    void clearSellerUnread(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("UPDATE ChatSession cs SET cs.buyerUnreadCount = 0 WHERE cs.id = :sessionId")
    void clearBuyerUnread(@Param("sessionId") Long sessionId);
}
