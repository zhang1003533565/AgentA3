package com.example.appbackend.repository;

import com.example.appbackend.entity.MeetingComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingCommentRepository extends JpaRepository<MeetingComment, Long> {

    List<MeetingComment> findByMeetingSessionIdOrderByCreateTimeAscIdAsc(Long meetingSessionId);
}
