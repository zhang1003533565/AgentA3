package com.example.appbackend.service;

import com.example.appbackend.dto.UserProfileDTO;

import java.util.Map;

public interface UserProfileService {

    UserProfileDTO.RadarSnapshot getSnapshot(Long userId);

    UserProfileDTO.RadarSnapshot getSnapshot(Long userId, String authorization);

    UserProfileDTO.AdminRulesResponse getRules();

    UserProfileDTO.EvidenceResponse addEvidence(Long userId, UserProfileDTO.EvidenceRequest request);

    Map<String, Object> buildLeaderProfileContext(Long userId);

    Map<String, Object> buildLeaderProfileContext(Long userId, String authorization);

    void refreshLeaderProfileContextAsync(Long userId, String authorization);
}
