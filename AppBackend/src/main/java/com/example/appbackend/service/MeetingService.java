package com.example.appbackend.service;

import com.example.appbackend.dto.MeetingDTO;
import com.example.appbackend.dto.PageResponse;

public interface MeetingService {

    MeetingDTO.SessionDetail createMeeting(Long userId, MeetingDTO.SessionRequest request);

    MeetingDTO.SessionDetail updateMeeting(Long userId, String sessionId, MeetingDTO.SessionRequest request);

    PageResponse<MeetingDTO.SessionItem> listMeetings(Long userId, Integer pageNum, Integer pageSize, String keyword);

    MeetingDTO.SessionDetail joinMeeting(Long userId, MeetingDTO.JoinRoomRequest request);

    MeetingDTO.SessionDetail getMeeting(Long userId, String sessionId);

    MeetingDTO.RecordItem addRecord(Long userId, String sessionId, MeetingDTO.RecordRequest request);

    MeetingDTO.RunAgentResponse runAgent(Long userId, String sessionId, MeetingDTO.RunAgentRequest request, String authorization);
}
