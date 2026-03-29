package com.example.appbackend.repository;

import com.example.appbackend.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    void deleteBySessionId(Long sessionId);

    Page<ChatMessage> findBySessionIdOrderByCreateTimeDesc(Long sessionId, Pageable pageable);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true WHERE cm.sessionId = :sessionId AND cm.senderId != :userId AND cm.isRead = false")
    void markAllReadBySessionAndUser(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
