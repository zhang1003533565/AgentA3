package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MeetingDTO;
import com.example.appbackend.entity.MeetingRecord;
import com.example.appbackend.service.MeetingAsrRecordService;
import com.example.appbackend.service.MeetingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MeetingAsrRecordServiceImpl implements MeetingAsrRecordService {

    private final MeetingService meetingService;

    public MeetingAsrRecordServiceImpl(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @Override
    public void saveFinalTranscript(Long userId, String sessionId, String transcript) {
        if (userId == null || !StringUtils.hasText(sessionId) || !StringUtils.hasText(transcript)) {
            return;
        }
        MeetingDTO.RecordRequest request = new MeetingDTO.RecordRequest();
        request.setSource(MeetingRecord.SOURCE_TRANSCRIPTION);
        request.setContent(transcript.trim());
        meetingService.addRecord(userId, sessionId, request);
    }
}
