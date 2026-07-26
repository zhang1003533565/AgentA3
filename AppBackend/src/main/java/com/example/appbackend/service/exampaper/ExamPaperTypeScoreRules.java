package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.TypeScoreRuleRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;

public final class ExamPaperTypeScoreRules {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExamPaperTypeScoreRules() {
    }

    public static String multipleChoiceRuleCode(CreateRequest request) {
        TypeScoreRuleRequest rule = multipleChoiceRule(request);
        if (rule == null || rule.getScoringRule() == null || rule.getScoringRule().isBlank()) {
            return null;
        }
        return rule.getScoringRule().trim().toLowerCase(Locale.ROOT);
    }

    public static String multipleChoiceRuleText(CreateRequest request) {
        TypeScoreRuleRequest rule = multipleChoiceRule(request);
        if (rule == null) return null;
        String code = rule.getScoringRule() == null ? "partial" : rule.getScoringRule().trim().toLowerCase(Locale.ROOT);
        if ("custom".equals(code)) {
            String custom = trimToNull(rule.getCustomScoringRule());
            return custom != null ? custom : trimToNull(rule.getScoringRuleText());
        }
        if ("strict".equals(code)) {
            return "全部选对得满分，少选、多选、错选均不得分";
        }
        return "少选得相应分，多选、错选不得分";
    }

    public static String enrichScoringJson(String scoringJson, String ruleCode, String ruleText) {
        if (ruleText == null || ruleText.isBlank()) return scoringJson;
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        if (ruleCode != null && !ruleCode.isBlank()) root.put("paperScoringRule", ruleCode);
        root.put("paperScoringRuleText", ruleText);
        if (scoringJson != null && !scoringJson.isBlank()) {
            try {
                JsonNode source = OBJECT_MAPPER.readTree(scoringJson);
                root.set("sourceScoring", source);
            } catch (Exception ignored) {
                root.put("sourceScoringText", scoringJson);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return scoringJson;
        }
    }

    public static String paperScoringRuleText(String scoringJson) {
        if (scoringJson == null || scoringJson.isBlank()) return null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(scoringJson);
            String text = root.path("paperScoringRuleText").asText("");
            return text.isBlank() ? null : text;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TypeScoreRuleRequest multipleChoiceRule(CreateRequest request) {
        Map<String, TypeScoreRuleRequest> rules = request == null ? null : request.getTypeScoreRules();
        return rules == null ? null : rules.get("multiple_choice");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
