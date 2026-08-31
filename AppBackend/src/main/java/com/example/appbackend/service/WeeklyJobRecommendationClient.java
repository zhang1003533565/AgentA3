package com.example.appbackend.service;

import java.util.List;

@FunctionalInterface
public interface WeeklyJobRecommendationClient {

    List<GeneratedJobRecommendation> generateRecommendations(String authorization) throws Exception;

    record GeneratedJobRecommendation(
            String jobTitle,
            String salary,
            String skills
    ) {
    }
}
