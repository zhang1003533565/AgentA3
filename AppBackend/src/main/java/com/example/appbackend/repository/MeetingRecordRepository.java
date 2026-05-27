package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRecordRepository extends JpaRepository<MeetingRecord, Long> {

    List<MeetingRecord> findByMeetingSessionIdOrderByCreateTimeAscIdAsc(Long meetingSessionId);

    long countByMeetingSessionId(Long meetingSessionId);
}
