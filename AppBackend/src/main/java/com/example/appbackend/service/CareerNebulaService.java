package com.example.appbackend.service;

import com.example.appbackend.entity.CareerNebulaMap;
import com.example.appbackend.repository.CareerNebulaMapRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CareerNebulaService {

    private static final String DEFAULT_MAP_KEY = "default";

    private final CareerNebulaMapRepository repository;
    private final ObjectMapper objectMapper;

    public CareerNebulaService(CareerNebulaMapRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> getMap() {
        return repository.findById(DEFAULT_MAP_KEY)
                .map(this::readPayload)
                .orElseGet(this::loadInitialMap);
    }

    @Transactional
    public Map<String, Object> saveMap(Map<String, Object> payload) {
        validatePayload(payload);
        CareerNebulaMap row = repository.findById(DEFAULT_MAP_KEY).orElseGet(CareerNebulaMap::new);
        row.setMapKey(DEFAULT_MAP_KEY);
        try {
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("岗位星图数据无法序列化", error);
        }
        repository.save(row);
        return payload;
    }

    private Map<String, Object> readPayload(CareerNebulaMap row) {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    row.getPayloadJson(), new TypeReference<>() { });
            validatePayload(payload);
            return payload;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("岗位星图数据库内容损坏", error);
        }
    }

    private void validatePayload(Map<String, Object> payload) {
        if (payload == null
                || !(payload.get("careers") instanceof List<?>)
                || !(payload.get("skills") instanceof List<?>)
                || !(payload.get("edges") instanceof List<?>)) {
            throw new IllegalArgumentException("岗位星图必须包含 careers、skills 和 edges 数组");
        }
    }

    private Map<String, Object> emptyMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("careers", new ArrayList<>());
        payload.put("skills", new ArrayList<>());
        payload.put("edges", new ArrayList<>());
        return payload;
    }

    private Map<String, Object> loadInitialMap() {
        ClassPathResource seed = new ClassPathResource("career-nebula-seed.json");
        if (!seed.exists()) {
            return saveMap(emptyMap());
        }
        try (var input = seed.getInputStream()) {
            Map<String, Object> payload = objectMapper.readValue(input, new TypeReference<>() { });
            return saveMap(payload);
        } catch (IOException error) {
            throw new IllegalStateException("岗位星图初始数据读取失败", error);
        }
    }
}
