package com.example.appbackend.service;

import com.example.appbackend.entity.CareerLearningProgress;
import com.example.appbackend.repository.CareerLearningProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CareerLearningProgressService {

    private final CareerLearningProgressRepository repository;
    private final CareerNebulaService careerNebulaService;

    public CareerLearningProgressService(
            CareerLearningProgressRepository repository,
            CareerNebulaService careerNebulaService
    ) {
        this.repository = repository;
        this.careerNebulaService = careerNebulaService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProgress(Long userId) {
        List<String> completedItemIds = repository.findAllByUserId(userId).stream()
                .filter(CareerLearningProgress::isCompleted)
                .map(CareerLearningProgress::getItemId)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completedItemIds", completedItemIds);
        return result;
    }

    @Transactional
    public Map<String, Object> updateProgress(
            Long userId,
            String careerId,
            String skillId,
            String itemId,
            boolean completed
    ) {
        validateLearningItem(careerId, skillId, itemId);
        CareerLearningProgress progress = repository.findByUserIdAndItemId(userId, itemId)
                .orElseGet(CareerLearningProgress::new);
        progress.setUserId(userId);
        progress.setCareerId(careerId);
        progress.setSkillId(skillId);
        progress.setItemId(itemId);
        progress.setCompleted(completed);
        progress.setCompletedAt(completed ? LocalDateTime.now() : null);
        repository.save(progress);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemId", itemId);
        result.put("completed", completed);
        return result;
    }

    private void validateLearningItem(String careerId, String skillId, String itemId) {
        if (isBlank(careerId) || isBlank(skillId) || isBlank(itemId)) {
            throw new IllegalArgumentException("岗位、学习星球和学习内容不能为空");
        }
        Object rawSkills = careerNebulaService.getMap().get("skills");
        if (!(rawSkills instanceof List<?> skillList)) {
            throw new IllegalArgumentException("岗位星图学习节点数据不可用");
        }
        boolean found = skillList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(skill -> skillId.equals(String.valueOf(skill.get("id"))))
                .filter(skill -> "enabled".equals(String.valueOf(skill.get("status"))))
                .filter(skill -> careerId.equals(String.valueOf(skill.get("careerId") == null ? "testing" : skill.get("careerId"))))
                .anyMatch(skill -> containsItem(skill.get("items"), itemId));
        if (!found) {
            throw new IllegalArgumentException("学习内容不存在或当前不可用");
        }
    }

    private boolean containsItem(Object rawItems, String itemId) {
        if (!(rawItems instanceof List<?> items)) return false;
        return items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(item -> itemId.equals(String.valueOf(item.get("id"))));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
