package com.example.appbackend.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionHealthControllerTest {

    @Test
    void exposesStableReadinessPayload() {
        SubmissionHealthController controller = new SubmissionHealthController();

        assertEquals(Map.of("status", "UP", "service", "app-backend"), controller.health());
    }
}
