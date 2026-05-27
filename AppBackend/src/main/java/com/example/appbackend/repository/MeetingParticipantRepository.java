package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingSessionIdOrderBySortOrderAscIdAsc(Long meetingSessionId);

    void deleteByMeetingSessionId(Long meetingSessionId);
}
