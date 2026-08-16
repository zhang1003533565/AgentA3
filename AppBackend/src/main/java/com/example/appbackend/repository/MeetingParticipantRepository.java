package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingSessionIdOrderBySortOrderAscIdAsc(Long meetingSessionId);

    Optional<MeetingParticipant> findByMeetingSessionIdAndName(Long meetingSessionId, String name);

    void deleteByMeetingSessionIdAndName(Long meetingSessionId, String name);

    void deleteByMeetingSessionId(Long meetingSessionId);
}
