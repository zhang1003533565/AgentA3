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
    public static final int OTHER = 99;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<FacilityTypeItem>> TYPE_LIST = new TypeReference<>() {};
    private static final String DEFAULT_COLOR = "#3b82f6";

    private static final List<FacilityTypeItem> DEFAULT_TYPES = List.of(
            new FacilityTypeItem(5, "其他", DEFAULT_COLOR)
    );

    @Autowired
    private MapConfigRepository mapConfigRepository;

    private volatile Map<Integer, FacilityTypeItem> typeCache = buildTypeMap(DEFAULT_TYPES);
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
            typeCache = buildTypeMap(normalized);
        } catch (Exception e) {
            throw new BusinessException(500, "保存设施类型失败");
        }
    }

    @Override
    public String getLabel(Integer type) {
        if (type == null) {
            return getType(OTHER).getLabel();
        }
        return getType(type).getLabel();
    }

    @Override
    public boolean isKnown(Integer type) {
        return type != null && typeCache.containsKey(type);
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
                                        typeCache = buildTypeMap(normalized);
                                    } catch (Exception ignored) {
                                        typeCache = buildTypeMap(DEFAULT_TYPES);
                                    }
                                });
                    } catch (Exception ignored) {
                        typeCache = buildTypeMap(DEFAULT_TYPES);
                    }
                    loaded = true;
                }
            }
        }
        return typeCache.values().stream()
                .map(this::copyType)
                .collect(Collectors.toList());
    }

    private List<FacilityTypeItem> parseTypes(String raw) {
        try {
            List<FacilityTypeItem> parsed = OBJECT_MAPPER.readValue(raw, TYPE_LIST);
            List<FacilityTypeItem> normalized = normalizeTypes(parsed);
            typeCache = buildTypeMap(normalized);
            return normalized;
        } catch (Exception e) {
            typeCache = buildTypeMap(DEFAULT_TYPES);
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
            unique.put(item.getValue(), new FacilityTypeItem(item.getValue(), item.getLabel().trim(), normalizeColor(item.getColor())));
        }
        if (!unique.containsKey(OTHER)) {
            unique.put(OTHER, new FacilityTypeItem(OTHER, "其他", DEFAULT_COLOR));
        }
        return unique.values().stream().collect(Collectors.toList());
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return DEFAULT_COLOR;
        }
        return color.trim();
    }

    private FacilityTypeItem getType(Integer value) {
        return typeCache.getOrDefault(value, typeCache.getOrDefault(OTHER, new FacilityTypeItem(OTHER, "其他", DEFAULT_COLOR)));
    }

    private FacilityTypeItem copyType(FacilityTypeItem item) {
        return new FacilityTypeItem(item.getValue(), item.getLabel(), normalizeColor(item.getColor()));
    }

    private Map<Integer, FacilityTypeItem> buildTypeMap(List<FacilityTypeItem> types) {
        Map<Integer, FacilityTypeItem> map = new LinkedHashMap<>();
        for (FacilityTypeItem item : types) {
            map.put(item.getValue(), copyType(item));
        }
        return map;
    }
}
