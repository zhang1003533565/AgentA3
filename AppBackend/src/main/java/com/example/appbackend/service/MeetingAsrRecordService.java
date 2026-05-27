package com.example.appbackend.service;

public interface MeetingAsrRecordService {

    void saveFinalTranscript(Long userId, String sessionId, String transcript);
}
