package com.example.appbackend.repository;

import com.example.appbackend.entity.AppMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AppMessageRepository extends JpaRepository<AppMessage, Long> {

    Page<AppMessage> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Optional<AppMessage> findBySourceTypeAndSourceIdAndUserIdAndEventType(String sourceType, Long sourceId, Long userId, String eventType);

    long countByUserIdAndIsReadFalse(Long userId);

    long countByUserIdAndModuleTypeAndIsReadFalse(Long userId, String moduleType);

    @Modifying
    @Query("UPDATE AppMessage am SET am.isRead = true, am.readTime = CURRENT_TIMESTAMP " +
            "WHERE am.userId = :userId AND am.isRead = false")
    int markAllReadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE AppMessage am SET am.isRead = true, am.readTime = CURRENT_TIMESTAMP " +
            "WHERE am.userId = :userId AND am.moduleType = :moduleType AND am.eventType IN :eventTypes AND am.isRead = false")
    int markReadByUserIdAndModuleTypeAndEventTypeIn(@Param("userId") Long userId,
                                                    @Param("moduleType") String moduleType,
                                                    @Param("eventTypes") Collection<String> eventTypes);

    @Modifying
    @Query("UPDATE AppMessage am SET am.isRead = true, am.readTime = CURRENT_TIMESTAMP " +
            "WHERE am.userId = :userId AND am.moduleType = :moduleType " +
            "AND am.eventType = :eventType AND am.sourceType = :sourceType AND am.isRead = false " +
            "AND am.sourceId IN (" +
            "SELECT cm.id FROM ChatMessage cm WHERE cm.sessionId = :sessionId AND cm.senderId <> :userId" +
            ")")
    int markChatMessagesReadBySession(@Param("userId") Long userId,
                                      @Param("sessionId") Long sessionId,
                                      @Param("moduleType") String moduleType,
                                      @Param("eventType") String eventType,
                                      @Param("sourceType") String sourceType);
}
