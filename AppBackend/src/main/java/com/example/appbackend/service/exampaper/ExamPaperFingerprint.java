package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.*;
import com.example.appbackend.entity.ExamQuestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Canonical render-input fingerprint shared by preview and persisted creation. */
public final class ExamPaperFingerprint {
    private ExamPaperFingerprint() { }

    public static Fingerprints compute(CreateRequest request, PaperLayoutConfig layout,
                                       List<FingerprintQuestion> questions) {
        return new Fingerprints(hash(configurationCanonical(request, layout)), hash(questionCanonical(questions)));
    }

    public static PaperLayoutConfig layout(PaperLayoutRequest source) {
        PaperLayoutConfig target = new PaperLayoutConfig();
        target.setRenderMode(source.getRenderMode()); target.setPageSize(source.getPageSize());
        target.setOrientation(source.getOrientation()); target.setMarginPreset(source.getMarginPreset());
        target.setCustomMarginTop(source.getCustomMarginTop()); target.setCustomMarginRight(source.getCustomMarginRight());
        target.setCustomMarginBottom(source.getCustomMarginBottom()); target.setCustomMarginLeft(source.getCustomMarginLeft());
        target.setColumnsCount(source.getColumnsCount()); target.setColumnSpace(source.getColumnSpace());
        target.setHasBindingLine(source.getHasBindingLine()); target.setHeaderInfo(source.getHeaderInfo());
        target.setTitleFontSize(source.getTitleFontSize()); target.setSubtitleFontSize(source.getSubtitleFontSize());
        target.setBodyFontSize(source.getBodyFontSize()); return target;
    }

    public static List<FingerprintQuestion> snapshot(List<SelectedQuestion> selections,
                                                      Map<Long, ExamQuestion> questions) {
        Map<String,Integer> sections = new LinkedHashMap<>();
        return selections.stream().sorted(Comparator.comparing(SelectedQuestion::getSortOrder)).map(selection -> {
            ExamQuestion q = questions.get(selection.getQuestionId());
            int section = sections.computeIfAbsent(q.getType(), ignored -> sections.size() + 1);
            return new FingerprintQuestion(q.getId(), selection.getSortOrder(), section, selection.getScore(),
                    q.getType(), q.getStem(), q.getBodyJson(), q.getAnswerJson(), q.getAnalysis(), q.getScoringJson());
        }).toList();
    }

    private static String configurationCanonical(CreateRequest request, PaperLayoutConfig layout) {
        StringBuilder out = new StringBuilder("exam-paper-config-v1");
        append(out, request.getTitle()); append(out, request.getSubtitle()); append(out, request.getDurationMinutes());
        append(out, request.getPrecautions()); append(out, request.getSelectionMode());
        append(out, layout.getRenderMode()); append(out, layout.getPageSize()); append(out, layout.getOrientation());
        append(out, layout.getMarginPreset()); append(out, layout.getCustomMarginTop()); append(out, layout.getCustomMarginRight());
        append(out, layout.getCustomMarginBottom()); append(out, layout.getCustomMarginLeft()); append(out, layout.getColumnsCount());
        append(out, layout.getColumnSpace()); append(out, layout.getHasBindingLine()); append(out, layout.getHeaderInfo());
        append(out, layout.getTitleFontSize()); append(out, layout.getSubtitleFontSize()); append(out, layout.getBodyFontSize());
        return out.toString();
    }

    private static String questionCanonical(List<FingerprintQuestion> questions) {
        StringBuilder out = new StringBuilder("exam-paper-questions-v1");
        for (FingerprintQuestion q : questions) {
            append(out, q.questionId()); append(out, q.sortOrder()); append(out, q.sectionOrder());
            append(out, q.score() == null ? null : q.score().stripTrailingZeros().toPlainString());
            append(out, q.type()); append(out, q.stem()); append(out, q.bodyJson()); append(out, q.answerJson());
            append(out, q.analysis()); append(out, q.scoringJson());
        }
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        out.append('|').append(text.length()).append(':').append(text);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    public record Fingerprints(String configurationHash, String questionHash) { }
    public record FingerprintQuestion(Long questionId, Integer sortOrder, Integer sectionOrder,
            java.math.BigDecimal score, String type, String stem, String bodyJson, String answerJson,
            String analysis, String scoringJson) { }
}
