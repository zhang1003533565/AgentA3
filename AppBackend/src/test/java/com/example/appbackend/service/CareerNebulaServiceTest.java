package com.example.appbackend.service;

import com.example.appbackend.entity.CareerNebulaMap;
import com.example.appbackend.repository.CareerNebulaMapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerNebulaServiceTest {

    private final CareerNebulaMapRepository repository = mock(CareerNebulaMapRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CareerNebulaService service = new CareerNebulaService(repository, objectMapper);

    @Test
    void loadsMigrationSeedAndPersistsItWhenDatabaseIsEmpty() {
        when(repository.findById("default")).thenReturn(Optional.empty());
        when(repository.save(any(CareerNebulaMap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = service.getMap();

        assertEquals(5, ((List<?>) payload.get("careers")).size());
        assertEquals(7, ((List<?>) payload.get("skills")).size());
        assertEquals(6, ((List<?>) payload.get("edges")).size());
        verify(repository).save(any(CareerNebulaMap.class));
    }

    @Test
    void rejectsIncompleteMapPayloads() {
        Map<String, Object> payload = Map.of("careers", List.of(), "skills", List.of());

        assertThrows(IllegalArgumentException.class, () -> service.saveMap(payload));
    }
}
