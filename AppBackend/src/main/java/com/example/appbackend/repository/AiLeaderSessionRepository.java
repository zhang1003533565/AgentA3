package com.example.appbackend.repository;

import com.example.appbackend.entity.AiLeaderSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiLeaderSessionRepository extends JpaRepository<AiLeaderSession, Long> {

    Optional<AiLeaderSession> findByUserIdAndSessionId(Long userId, String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session FROM AiLeaderSession session
            WHERE session.userId = :userId AND session.sessionId = :sessionId
            """)
    Optional<AiLeaderSession> findForUpdateByUserIdAndSessionId(@Param("userId") Long userId,
                                                                @Param("sessionId") String sessionId);

    Page<AiLeaderSession> findByUserIdOrderByUpdateTimeDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT session FROM AiLeaderSession session
            WHERE session.userId = :userId
              AND (
                :keyword IS NULL
                OR :keyword = ''
                OR LOWER(session.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(session.lastMessage) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY session.updateTime DESC
            """)
    Page<AiLeaderSession> searchByUserId(@Param("userId") Long userId,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);
}
