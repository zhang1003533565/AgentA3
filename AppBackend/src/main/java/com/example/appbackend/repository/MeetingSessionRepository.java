package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingParticipant;
import com.example.appbackend.entity.MeetingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MeetingSessionRepository extends JpaRepository<MeetingSession, Long> {

    Optional<MeetingSession> findByUserIdAndSessionId(Long userId, String sessionId);

    Optional<MeetingSession> findBySessionId(String sessionId);

    Optional<MeetingSession> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    boolean existsByUserIdAndStatus(Long userId, String status);

    boolean existsByUserIdAndStatusAndScheduledStartTime(Long userId, String status, LocalDateTime scheduledStartTime);

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

    /**
     * 主持人或参会人可见的会议查询：
     * session.userId = :userId（主持人本人）
     * OR meeting_participant 中存在 meeting_session_id = session.id 且 name = :displayName（已加入的参会人）
     */
    @Query("""
            SELECT session FROM MeetingSession session
            WHERE (
                  session.userId = :userId
                  OR session.id IN (
                      SELECT participant.meetingSessionId FROM MeetingParticipant participant
                      WHERE participant.name = :displayName
                  )
              )
              AND (
                :keyword IS NULL
                OR :keyword = ''
                OR LOWER(session.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(session.lastNote) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY session.updateTime DESC
            """)
    Page<MeetingSession> searchAccessibleByUserId(@Param("userId") Long userId,
                                                  @Param("displayName") String displayName,
                                                  @Param("keyword") String keyword,
                                                  Pageable pageable);
}
