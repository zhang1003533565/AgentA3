package com.example.appbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 章节 material_ids 字段（JSON 数组）的安全读写工具。
 * 统一使用项目已内置的 Jackson，禁止使用 fastjson；对 NULL、空串、[] 做防空处理。
 */
@Component
public class MaterialIdsCodec {

    private final ObjectMapper objectMapper;

    public MaterialIdsCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析 JSON 数组为去重、保序的 Long 列表；解析失败或空值返回空列表。 */
    public List<Long> parse(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            Set<Long> ordered = new LinkedHashSet<>();
            for (Long id : ids) {
                if (id != null) {
                    ordered.add(id);
                }
            }
            return new ArrayList<>(ordered);
        } catch (Exception error) {
            return new ArrayList<>();
        }
    }

    /** 序列化为 JSON 数组字符串；空列表返回 "[]"。 */
    public String write(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception error) {
            return "[]";
        }
    }

    /** 判断某章节的 material_ids 是否引用了指定资料 ID。 */
    public boolean contains(String json, Long materialId) {
        return materialId != null && parse(json).contains(materialId);
    }
}
