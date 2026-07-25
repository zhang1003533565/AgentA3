package com.example.appbackend.service;

import java.util.Map;

@FunctionalInterface
public interface SubmissionDependencyProbe {
    Map<String, String> probe();
}
