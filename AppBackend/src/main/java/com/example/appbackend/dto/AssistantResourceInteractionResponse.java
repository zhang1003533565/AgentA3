package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantResourceInteractionResponse {

    private String status;
    private boolean duplicate;
    private String sourceId;
    private UserProfileDTO.EvidenceResponse profileEvidence;
}
