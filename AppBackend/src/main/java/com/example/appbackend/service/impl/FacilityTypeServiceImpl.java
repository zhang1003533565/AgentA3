package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FacilityTypeItem;
import com.example.appbackend.entity.MapConfig;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MapConfigRepository;
import com.example.appbackend.service.FacilityTypeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FacilityTypeServiceImpl implements FacilityTypeService {

    public static final String CONFIG_KEY = "facility_types";
    public static final int OTHER = 5;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<FacilityTypeItem>> TYPE_LIST = new TypeReference<>() {};

    private static final List<FacilityTypeItem> DEFAULT_TYPES = List.of(
            new FacilityTypeItem(1, "食堂"),
            new FacilityTypeItem(2, "运动场"),
            new FacilityTypeItem(5, "其他"),
            new FacilityTypeItem(6, "教学楼"),
            new FacilityTypeItem(7, "宿舍")
    );

    @Autowired
    private MapConfigRepository mapConfigRepository;

    private volatile Map<Integer, String> labelCache = buildLabelMap(DEFAULT_TYPES);
    private volatile boolean loaded = false;

    @Override
    public List<FacilityTypeItem> listTypes() {
        return new ArrayList<>(loadTypesFromConfig());
    }

    @Override
    @Transactional
    public void saveTypes(List<FacilityTypeItem> types) {
        List<FacilityTypeItem> normalized = normalizeTypes(types);
        try {
            String json = OBJECT_MAPPER.writeValueAsString(normalized);
            MapConfig config = mapConfigRepository.findByConfigKey(CONFIG_KEY).orElseGet(() -> {
                MapConfig created = new MapConfig();
                created.setConfigKey(CONFIG_KEY);
                created.setDescription("设施类型字典");
                created.setCreateTime(LocalDateTime.now());
                return created;
            });
            config.setConfigValue(json);
            config.setUpdateTime(LocalDateTime.now());
            mapConfigRepository.save(config);
            labelCache = buildLabelMap(normalized);
        } catch (Exception e) {
            throw new BusinessException(500, "保存设施类型失败");
        }
    }

    @Override
    public String getLabel(Integer type) {
        if (type == null) {
            return labelCache.getOrDefault(OTHER, "其他");
        }
        int normalizedType = type >= 2 && type <= 4 ? 2 : type == 99 ? OTHER : type;
        return labelCache.getOrDefault(normalizedType, labelCache.getOrDefault(OTHER, "其他"));
    }

    @Override
    public boolean isKnown(Integer type) {
        if (type == null) {
            return false;
        }
        int normalizedType = type >= 2 && type <= 4 ? 2 : type == 99 ? OTHER : type;
        return labelCache.containsKey(normalizedType);
    }

    private List<FacilityTypeItem> loadTypesFromConfig() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    try {
                        mapConfigRepository.findByConfigKey(CONFIG_KEY)
                                .map(MapConfig::getConfigValue)
                                .filter(raw -> raw != null && !raw.isBlank())
                                .ifPresent(raw -> {
                                    try {
                                        List<FacilityTypeItem> parsed = OBJECT_MAPPER.readValue(raw, TYPE_LIST);
                                        List<FacilityTypeItem> normalized = normalizeTypes(parsed);
                                        labelCache = buildLabelMap(normalized);
                                    } catch (Exception ignored) {
                                        labelCache = buildLabelMap(DEFAULT_TYPES);
                                    }
                                });
                    } catch (Exception ignored) {
                        labelCache = buildLabelMap(DEFAULT_TYPES);
                    }
                    loaded = true;
                }
            }
        }
        return labelCache.entrySet().stream()
                .map(e -> new FacilityTypeItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<FacilityTypeItem> parseTypes(String raw) {
        try {
            List<FacilityTypeItem> parsed = OBJECT_MAPPER.readValue(raw, TYPE_LIST);
            List<FacilityTypeItem> normalized = normalizeTypes(parsed);
            labelCache = buildLabelMap(normalized);
            return normalized;
        } catch (Exception e) {
            labelCache = buildLabelMap(DEFAULT_TYPES);
            return DEFAULT_TYPES;
        }
    }

    private List<FacilityTypeItem> normalizeTypes(List<FacilityTypeItem> types) {
        if (types == null || types.isEmpty()) {
            throw new BusinessException(400, "设施类型列表不能为空");
        }
        Map<Integer, FacilityTypeItem> unique = new LinkedHashMap<>();
        for (FacilityTypeItem item : types) {
            if (item == null || item.getValue() == null || item.getLabel() == null || item.getLabel().isBlank()) {
                throw new BusinessException(400, "设施类型编码与名称不能为空");
            }
            if (item.getValue() <= 0) {
                throw new BusinessException(400, "设施类型编码必须大于 0");
            }
            int normalizedValue = item.getValue() >= 2 && item.getValue() <= 4
                    ? 2
                    : item.getValue() == 99 ? OTHER : item.getValue();
            String normalizedLabel = normalizedValue == 2
                    ? "运动场"
                    : normalizedValue == OTHER ? "其他" : item.getLabel().trim();
            unique.put(normalizedValue, new FacilityTypeItem(normalizedValue, normalizedLabel));
        }
        if (!unique.containsKey(OTHER)) {
            unique.put(OTHER, new FacilityTypeItem(OTHER, "其他"));
        }
        return unique.values().stream().collect(Collectors.toList());
    }

    private Map<Integer, String> buildLabelMap(List<FacilityTypeItem> types) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (FacilityTypeItem item : types) {
            map.put(item.getValue(), item.getLabel());
        }
        return map;
    }
}
