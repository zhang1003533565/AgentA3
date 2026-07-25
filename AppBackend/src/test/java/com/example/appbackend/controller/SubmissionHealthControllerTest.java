package com.example.appbackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionHealthControllerTest {

    @Test
    void exposesStableReadinessPayload() {
        SubmissionHealthController controller = new SubmissionHealthController(
                () -> Map.of("database", "UP", "redis", "UP", "aiServer", "UP"));

        assertEquals(Map.of("status", "UP", "service", "app-backend"), controller.health());

        ResponseEntity<Map<String, Object>> response = controller.readiness();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals(
                Map.of("database", "UP", "redis", "UP", "aiServer", "UP"),
                response.getBody().get("components"));
    }

    @Test
    void readinessFailsClosedWhenAnyRequiredDependencyIsDown() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", "UP");
        components.put("redis", "DOWN");
        components.put("aiServer", "UP");
        SubmissionHealthController controller = new SubmissionHealthController(() -> components);

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
    }
}
