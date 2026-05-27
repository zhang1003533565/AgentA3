package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MeetingSessionRepository extends JpaRepository<MeetingSession, Long> {

    Optional<MeetingSession> findByUserIdAndSessionId(Long userId, String sessionId);

    @Query("""
            SELECT session FROM MeetingSession session
            WHERE session.userId = :userId
              AND (
                :keyword IS NULL
                OR :keyword = ''
                OR LOWER(session.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(session.lastNote) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY session.updateTime DESC
            """)
    Page<MeetingSession> searchByUserId(@Param("userId") Long userId,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);
}
