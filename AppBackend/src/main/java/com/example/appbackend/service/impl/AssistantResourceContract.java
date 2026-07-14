package com.example.appbackend.service.impl;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class AssistantResourceContract {

    static final int MAX_ID_LENGTH = 80;
    static final int MAX_TITLE_LENGTH = 240;

    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9:_-]{1,80}");
    private static final Map<String, Set<String>> KIND_DELIVERIES = Map.ofEntries(
            Map.entry("explanation", Set.of("content", "document", "presentation")),
            Map.entry("mind_map", Set.of("content", "image", "document")),
            Map.entry("diagram", Set.of("content", "image", "document")),
            Map.entry("exercise", Set.of("content", "document", "spreadsheet")),
            Map.entry("code_example", Set.of("content", "document", "bundle")),
            Map.entry("extended_reading", Set.of("content", "document")),
            Map.entry("image", Set.of("image")),
            Map.entry("video", Set.of("video")),
            Map.entry("audio", Set.of("audio")),
            Map.entry("document", Set.of("document")),
            Map.entry("presentation", Set.of("presentation")),
            Map.entry("spreadsheet", Set.of("spreadsheet")),
            Map.entry("bundle", Set.of("bundle")),
            Map.entry("course", Set.of("business_card")),
            Map.entry("activity", Set.of("business_card")),
            Map.entry("meeting", Set.of("business_card")),
            Map.entry("dining", Set.of("business_card")),
            Map.entry("facility", Set.of("business_card")),
            Map.entry("secondhand", Set.of("business_card")));

    private AssistantResourceContract() {
    }

    static boolean isValidResourceId(String id) {
        return id != null && RESOURCE_ID.matcher(id).matches();
    }

    static boolean isValidCore(String schemaVersion,
                               String id,
                               String kind,
                               String deliveryType,
                               String title) {
        return "assistant-resource-v1".equals(schemaVersion)
                && isValidResourceId(id)
                && KIND_DELIVERIES.getOrDefault(kind, Set.of()).contains(deliveryType)
                && title != null
                && !title.isBlank()
                && title.length() <= MAX_TITLE_LENGTH
                && title.codePoints().noneMatch(Character::isISOControl);
    }
}
