package com.example.appbackend.controller;

import com.example.appbackend.service.SubmissionDependencyProbe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/actuator")
public class SubmissionHealthController {
    private final SubmissionDependencyProbe dependencyProbe;

    public SubmissionHealthController(SubmissionDependencyProbe dependencyProbe) {
        this.dependencyProbe = dependencyProbe;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "app-backend");
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, String> components = dependencyProbe.probe();
        boolean ready = !components.isEmpty()
                && components.values().stream().allMatch("UP"::equals);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ready ? "UP" : "DOWN");
        body.put("service", "app-backend");
        body.put("components", components);
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
