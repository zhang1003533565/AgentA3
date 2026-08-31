package com.example.appbackend.service;

import com.example.appbackend.entity.CareerLearningProgress;
import com.example.appbackend.repository.CareerLearningProgressRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerLearningProgressServiceTest {

    private final CareerLearningProgressRepository repository = mock(CareerLearningProgressRepository.class);
    private final CareerNebulaService careerNebulaService = mock(CareerNebulaService.class);
    private final CareerLearningProgressService service =
            new CareerLearningProgressService(repository, careerNebulaService);

    @Test
    void returnsOnlyCompletedItems() {
        CareerLearningProgress completed = progress("item-1", true);
        CareerLearningProgress pending = progress("item-2", false);
        when(repository.findAllByUserId(7L)).thenReturn(List.of(completed, pending));

        Map<String, Object> result = service.getProgress(7L);

        assertEquals(List.of("item-1"), result.get("completedItemIds"));
    }

    @Test
    void savesCompletedStateForAnEnabledLearningItem() {
        when(careerNebulaService.getMap()).thenReturn(validMap());
        when(repository.findByUserIdAndItemId(7L, "item-1")).thenReturn(Optional.empty());

        Map<String, Object> result = service.updateProgress(7L, "testing", "foundation", "item-1", true);

        ArgumentCaptor<CareerLearningProgress> captor = ArgumentCaptor.forClass(CareerLearningProgress.class);
        verify(repository).save(captor.capture());
        CareerLearningProgress saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("testing", saved.getCareerId());
        assertEquals("foundation", saved.getSkillId());
        assertEquals("item-1", saved.getItemId());
        assertNotNull(saved.getCompletedAt());
        assertEquals(true, result.get("completed"));
    }

    @Test
    void rejectsUnknownLearningItems() {
        when(careerNebulaService.getMap()).thenReturn(validMap());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProgress(7L, "testing", "foundation", "missing", true));
    }

    private CareerLearningProgress progress(String itemId, boolean completed) {
        CareerLearningProgress progress = new CareerLearningProgress();
        progress.setItemId(itemId);
        progress.setCompleted(completed);
        return progress;
    }

    private Map<String, Object> validMap() {
        return Map.of("skills", List.of(Map.of(
                "id", "foundation",
                "careerId", "testing",
                "status", "enabled",
                "items", List.of(Map.of("id", "item-1", "title", "软件测试基本概念"))
        )));
    }
}
