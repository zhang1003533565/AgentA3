package com.example.appbackend.service;

public record FileSummaryResult(
        String summary,
        String status,
        String model,
        String centerTopic
) {
    public FileSummaryResult(String summary, String status, String model) {
        this(summary, status, model, "");
    }
}
