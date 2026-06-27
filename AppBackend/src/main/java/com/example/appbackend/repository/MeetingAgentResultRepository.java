package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingAgentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingAgentResultRepository extends JpaRepository<MeetingAgentResult, Long> {

    List<MeetingAgentResult> findByMeetingSessionIdOrderByCreateTimeDescIdDesc(Long meetingSessionId);

    long countByMeetingSessionId(Long meetingSessionId);

    boolean existsByMeetingSessionIdAndAgentName(Long meetingSessionId, String agentName);

    void deleteByMeetingSessionId(Long meetingSessionId);
}
