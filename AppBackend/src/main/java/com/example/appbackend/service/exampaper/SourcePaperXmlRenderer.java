package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.service.exampaper.SourcePaperLayoutResolver.ResolvedPageLayout;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OOXML fragment renderer ported from generatePaper.js without reformatting the source template. */
public final class SourcePaperXmlRenderer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(\\d+);");
    private static final Pattern NEWLINES = Pattern.compile("\\n+");
    private static final Pattern BLANK_PLACEHOLDER = Pattern.compile("\\{\\{blank_[^}]+}}");
    private static final int SOURCE_ESSAY_LINES = 12;
    private final ObjectMapper objectMapper;

    public SourcePaperXmlRenderer() {
        this(new ObjectMapper());
    }

    SourcePaperXmlRenderer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public String renderSubtitle(PaperVO paper, PaperLayoutConfig layout) {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(layout, "layout");
        if (paper.getSubtitle() == null || paper.getSubtitle().isBlank()) return "";
        return "<w:p w:rsidR=\"00D8177A\" w:rsidRDefault=\"00D8177A\" w:rsidP=\"00C51EF4\">"
                + "<w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr>"
                + "<w:rFonts w:hint=\"eastAsia\"/><w:sz w:val=\"" + layout.getSubtitleFontSize() + "\"/>"
                + "<w:szCs w:val=\"" + layout.getSubtitleFontSize() + "\"/></w:rPr>"
                + "<w:t>" + escapeXml(paper.getSubtitle()) + "</w:t></w:r></w:p>";
    }

    public String renderScoreTable(PaperVO paper) {
        int count = sections(paper == null ? null : paper.getQuestions()).size();
        return renderScoreTable(count);
    }

    public String renderScoreTable(int sectionCount) {
        List<String> headings = new ArrayList<>();
        headings.add("题号");
        for (int index = 1; index <= sectionCount; index++) headings.add(toChineseNumber(index));
        StringBuilder header = new StringBuilder("<w:tr>");
        headings.forEach(value -> header.append(tableCell(value, 1000, true, 21)));
        header.append("</w:tr>");
        StringBuilder score = new StringBuilder("<w:tr><w:trPr><w:trHeight w:val=\"520\" w:hRule=\"atLeast\"/></w:trPr>");
        score.append(tableCell("评分", 1000, true, 21));
        for (int index = 0; index < sectionCount; index++) score.append(tableCell("", 1000, false, 21));
        score.append("</w:tr>");
        return "<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/>"
                + "<w:jc w:val=\"center\"/>" + singleBorders() + "</w:tblPr>" + header + score + "</w:tbl>";
    }

    public String renderSectionHeader(String title) {
        String grader = "<w:tbl><w:tblPr><w:tblW w:w=\"1938\" w:type=\"dxa\"/>" + singleBorders() + "</w:tblPr>"
                + graderRow("阅卷人") + graderRow("得分") + "</w:tbl>";
        return "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblLayout w:type=\"fixed\"/>"
                + noneBorders() + "</w:tblPr><w:tblGrid><w:gridCol w:w=\"1938\"/><w:gridCol w:w=\"7426\"/></w:tblGrid><w:tr>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"1938\" w:type=\"dxa\"/>" + cellNoneBorders() + "</w:tcPr>"
                + grader + "<w:p/></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"7426\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/>"
                + cellNoneBorders() + "</w:tcPr>" + paragraph(title, true, 21, "left", 0, 0) + "</w:tc>"
                + "</w:tr></w:tbl>";
    }

    public String renderQuestions(PaperVO paper, PaperLayoutConfig layout) {
        Objects.requireNonNull(layout, "layout");
        List<QuestionSection> sections = sections(paper == null ? null : paper.getQuestions());
        StringBuilder xml = new StringBuilder();
        int questionNumber = 0;
        for (int index = 0; index < sections.size(); index++) {
            QuestionSection section = sections.get(index);
            String title = toChineseNumber(index + 1) + "、" + typeName(section.type()) + "(共"
                    + section.questions().size() + "题, 共" + score(section.questions()) + "分";
            String instruction = scoringInstruction(section);
            if (instruction != null) {
                title += "，" + instruction;
            }
            title += ")";
            xml.append(renderSectionHeader(title));
            for (QuestionSnapshotVO question : section.questions()) {
                questionNumber++;
                if (isChoice(question.getType())) {
                    boolean singleChoice = normalizedType(question.getType()).equals("single_choice")
                            || normalizedType(question.getType()).equals("single")
                            || normalizedType(question.getType()).equals("1");
                    xml.append(choiceQuestion(questionNumber, question, layout.getBodyFontSize(), false, singleChoice));
                } else if (isJudgment(question.getType())) {
                    xml.append(choiceQuestion(questionNumber, question, layout.getBodyFontSize(), true, true));
                } else if (isFillBlank(question.getType())) {
                    xml.append(fillBlankQuestion(questionNumber, question, layout.getBodyFontSize()));
                } else if (isSubjective(question.getType())) {
                    xml.append(essayQuestion(questionNumber, question, layout.getBodyFontSize(),
                            isStructuredSubjective(question.getType())));
                } else {
                    xml.append(fallbackQuestion(questionNumber, question, layout.getBodyFontSize()));
                }
            }
            xml.append("<w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr></w:p>");
        }
        return xml.toString();
    }

    public String renderAnswers(PaperVO paper, PaperLayoutConfig layout) {
        Objects.requireNonNull(layout, "layout");
        List<QuestionSection> sections = sections(paper == null ? null : paper.getQuestions());
        StringBuilder xml = new StringBuilder("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
        xml.append(paragraph("答案解析", true, layout.getBodyFontSize(), "center", 200, 0));
        int questionNumber = 0;
        for (int index = 0; index < sections.size(); index++) {
            QuestionSection section = sections.get(index);
            xml.append(paragraph(toChineseNumber(index + 1) + "、" + typeName(section.type()), true,
                    layout.getBodyFontSize(), "left", 120, 0));
            for (QuestionSnapshotVO question : section.questions()) {
                questionNumber++;
                xml.append(paragraph(questionNumber + "．答案:" + answerText(question), false,
                        layout.getBodyFontSize(), "left", 80, 0));
                if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
                    xml.append(paragraph("解析:" + cleanHtml(question.getAnalysis()), false,
                            layout.getBodyFontSize(), "left", 80, 0));
                }
            }
        }
        return xml.toString();
    }

    public String renderPageSettings(ResolvedPageLayout layout) {
        Objects.requireNonNull(layout, "layout");
        return layout.pageSizeXml() + layout.pageMarginsXml() + layout.pageNumberingXml()
                + layout.columnsXml() + layout.titlePageXml() + layout.documentGridXml();
    }

    private String choiceQuestion(int number, QuestionSnapshotVO question, int fontSize, boolean judgment, boolean singleChoice) {
        StringBuilder xml = new StringBuilder(paragraph(number + "．" + cleanHtml(question.getStem()) + "(" + plainScore(question) + "分)",
                false, fontSize, "left", 80, 0));
        List<Option> options = judgment
                ? List.of(new Option("A", "A", "正确"), new Option("B", "B", "错误"))
                : options(question.getBodyJson());
        if (!options.isEmpty()) {
            int maxLength = options.stream().mapToInt(option -> cleanHtml(option.text()).length()).max().orElse(0);
            if (judgment || (singleChoice && maxLength < 15 && options.size() <= 4)) {
                // 单选题每行放2个选项，避免一行放不下4个时出现3+1的尴尬换行
                for (int i = 0; i < options.size(); i += 2) {
                    List<String> row = new ArrayList<>();
                    for (int j = i; j < Math.min(i + 2, options.size()); j++) {
                        row.add(optionText(options.get(j)));
                    }
                    xml.append(paragraph(String.join("        ", row), false, fontSize, "left", 80, 420));
                }
            } else {
                options.forEach(option -> xml.append(paragraph(optionText(option), false, fontSize, "left", 80, 420)));
            }
        }
        xml.append("<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr></w:p>");
        return xml.toString();
    }

    private String essayQuestion(int number, QuestionSnapshotVO question, int fontSize, boolean includeStructuredBody) {
        StringBuilder xml = new StringBuilder(paragraph(number + "．[简答题]" + cleanHtml(question.getStem()) + "(" + plainScore(question) + "分)",
                false, fontSize, "left", 200, 0));
        if (includeStructuredBody) {
            String body = completeStructuredBody(question.getBodyJson());
            if (!body.isBlank()) xml.append(paragraph(body, false, fontSize, "left", 80, 0));
        }
        for (int index = 0; index < SOURCE_ESSAY_LINES; index++) {
            xml.append("<w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr></w:p>");
        }
        return xml.toString();
    }

    private String fallbackQuestion(int number, QuestionSnapshotVO question, int fontSize) {
        StringBuilder xml = new StringBuilder(paragraph(number + "．" + cleanHtml(question.getStem()) + "(" + plainScore(question) + "分)",
                false, fontSize, "left", 80, 0));
        String body = readableBody(question.getBodyJson());
        if (!body.isBlank()) xml.append(paragraph(body, false, fontSize, "left", 80, 0));
        xml.append("<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr></w:p>");
        return xml.toString();
    }

    private String fillBlankQuestion(int number, QuestionSnapshotVO question, int fontSize) {
        String stem = BLANK_PLACEHOLDER.matcher(cleanHtml(question.getStem())).replaceAll("________");
        return paragraph(number + "．" + stem + "(" + plainScore(question) + "分)",
                false, fontSize, "left", 80, 0)
                + "<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr></w:p>";
    }

    private List<Option> options(String json) {
        JsonNode body = readJson(json);
        if (body == null || !body.path("options").isArray()) return List.of();
        List<Option> options = new ArrayList<>();
        int index = 0;
        for (JsonNode option : body.path("options")) {
            String originalKey = option.path("key").asText("");
            String text = option.path("text").asText("");
            options.add(new Option(String.valueOf((char) ('A' + index)), originalKey, text));
            index++;
        }
        return options;
    }

    private String readableBody(String json) {
        JsonNode node = readJson(json);
        if (node == null) return cleanHtml(json);
        if (node.isNull() || node.isEmpty()) return "";
        List<String> values = new ArrayList<>();
        for (String key : List.of("statement", "text", "material", "task", "description", "inputFormat", "outputFormat")) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) values.add(cleanHtml(value.asText()));
        }
        if (!values.isEmpty()) return String.join(" ", values);
        return pretty(node);
    }

    private String completeStructuredBody(String json) {
        JsonNode node = readJson(json);
        if (node == null) return cleanHtml(json);
        if (node.isNull() || node.isEmpty()) return "";
        // Pretty JSON deliberately preserves every nested field (including material subQuestions)
        // while paragraph XML escaping makes the complete recursive structure safe for OOXML.
        return pretty(node);
    }

    private String answerText(QuestionSnapshotVO question) {
        JsonNode answer = readJson(question.getAnswerJson());
        if (answer == null) return cleanHtml(question.getAnswerJson());
        if (answer.isNull() || answer.isEmpty()) return "";
        if (answer.has("correctOption")) return mapAnswerKey(question, answer.path("correctOption").asText());
        if (answer.path("correctOptions").isArray()) {
            List<String> options = new ArrayList<>();
            answer.path("correctOptions").forEach(item -> options.add(mapAnswerKey(question, item.asText())));
            return String.join(",", options);
        }
        if (answer.has("correct") && answer.path("correct").isBoolean()) return answer.path("correct").asBoolean() ? "正确" : "错误";
        if (answer.path("blanks").isArray()) {
            List<String> blanks = new ArrayList<>();
            for (JsonNode blank : answer.path("blanks")) {
                JsonNode accepted = blank.path("answers");
                if (accepted.isArray() && !accepted.isEmpty()) blanks.add(cleanHtml(accepted.get(0).asText()));
            }
            return String.join("；", blanks);
        }
        for (String key : List.of("referenceAnswer", "finalAnswer", "conclusion", "expectedResult")) {
            if (answer.has(key)) return cleanHtml(answer.path(key).asText());
        }
        return pretty(answer);
    }

    private String mapAnswerKey(QuestionSnapshotVO question, String originalKey) {
        for (Option option : options(question.getBodyJson())) {
            if (Objects.equals(option.originalKey(), originalKey)) return option.label();
        }
        return cleanHtml(originalKey);
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String pretty(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException ignored) {
            return node.toString();
        }
    }

    private List<QuestionSection> sections(List<QuestionSnapshotVO> questions) {
        if (questions == null) return List.of();
        List<QuestionSnapshotVO> ordered = questions.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(QuestionSnapshotVO::getSectionOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(QuestionSnapshotVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<SectionKey, List<QuestionSnapshotVO>> grouped = new LinkedHashMap<>();
        for (QuestionSnapshotVO question : ordered) {
            grouped.computeIfAbsent(new SectionKey(question.getSectionOrder(), normalizedType(question.getType())), ignored -> new ArrayList<>())
                    .add(question);
        }
        return grouped.entrySet().stream().map(entry -> new QuestionSection(entry.getKey().type(), entry.getValue())).toList();
    }

    private String paragraph(String text, boolean bold, int fontSize, String alignment, int spacing, int indent) {
        StringBuilder properties = new StringBuilder();
        if (!"left".equals(alignment) || indent > 0 || spacing > 0) {
            properties.append("<w:pPr>");
            if (!"left".equals(alignment)) properties.append("<w:jc w:val=\"").append(alignment).append("\"/>");
            if (indent > 0) properties.append("<w:ind w:left=\"").append(indent).append("\"/>");
            if (spacing > 0) properties.append("<w:spacing w:after=\"").append(spacing).append("\"/>");
            properties.append("</w:pPr>");
        }
        return "<w:p>" + properties + "<w:r><w:rPr><w:rFonts w:eastAsia=\"宋体\" w:hint=\"eastAsia\"/>"
                + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + fontSize + "\"/><w:szCs w:val=\"" + fontSize
                + "\"/></w:rPr>" + textWithLineBreaks(text) + "</w:r></w:p>";
    }

    private String textWithLineBreaks(String text) {
        String[] lines = (text == null ? "" : text).split("\\R", -1);
        StringBuilder xml = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) xml.append("<w:br/>");
            xml.append("<w:t>").append(escapeXml(lines[index])).append("</w:t>");
        }
        return xml.toString();
    }

    private String tableCell(String text, int width, boolean bold, int fontSize) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr>"
                + paragraph(text, bold, fontSize, "center", 0, 0) + "</w:tc>";
    }

    private String graderRow(String label) {
        return "<w:tr><w:trPr><w:trHeight w:val=\"549\" w:hRule=\"atLeast\"/></w:trPr>"
                + tableCell(label, 969, true, 21) + tableCell("", 969, false, 21) + "</w:tr>";
    }

    private String singleBorders() {
        return "<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/>"
                + "<w:left w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/>"
                + "<w:right w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/>"
                + "<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/>"
                + "<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/></w:tblBorders>";
    }

    private String noneBorders() {
        return "<w:tblBorders><w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/>"
                + "<w:right w:val=\"none\"/><w:insideH w:val=\"none\"/><w:insideV w:val=\"none\"/></w:tblBorders>";
    }

    private String cellNoneBorders() {
        return "<w:tcBorders><w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/>"
                + "<w:right w:val=\"none\"/></w:tcBorders>";
    }

    private String optionText(Option option) {
        return option.label() + ". " + cleanHtml(option.text());
    }

    private String plainScore(QuestionSnapshotVO question) {
        return question.getScore() == null ? "" : question.getScore().stripTrailingZeros().toPlainString();
    }

    private String score(List<QuestionSnapshotVO> questions) {
        BigDecimal result = questions.stream().map(QuestionSnapshotVO::getScore).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return result.stripTrailingZeros().toPlainString();
    }

    private String scoringInstruction(QuestionSection section) {
        if (!normalizedType(section.type()).equals("multiple_choice") || section.questions().isEmpty()) return null;
        return ExamPaperTypeScoreRules.paperScoringRuleText(section.questions().get(0).getScoringJson());
    }

    static String cleanHtml(String value) {
        if (value == null) return "";
        String clean = HTML_TAG.matcher(value).replaceAll("")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"");
        Matcher matcher = NUMERIC_ENTITY.matcher(clean);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            // JavaScript String.fromCharCode applies ToUint16; BigInteger keeps that behavior safe
            // even when the decimal entity is far beyond Number/long range.
            int codeUnit = new BigInteger(matcher.group(1)).and(BigInteger.valueOf(0xffffL)).intValue();
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(String.valueOf((char) codeUnit)));
        }
        matcher.appendTail(decoded);
        return NEWLINES.matcher(decoded).replaceAll(" ").trim();
    }

    static String escapeXml(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().filter(SourcePaperXmlRenderer::isXmlCharacter).forEach(safe::appendCodePoint);
        return safe.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static boolean isXmlCharacter(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }

    private String toChineseNumber(int number) {
        String[] chinese = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
        if (number <= 10) return chinese[number - 1];
        if (number < 20) return "十" + (number % 10 == 0 ? "" : chinese[number % 10 - 1]);
        return Integer.toString(number);
    }

    private String normalizedType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    private boolean isChoice(String type) {
        String normalized = normalizedType(type);
        return normalized.equals("single_choice") || normalized.equals("multiple_choice") || normalized.equals("single")
                || normalized.equals("multiple") || normalized.equals("1") || normalized.equals("2");
    }

    private boolean isJudgment(String type) {
        String normalized = normalizedType(type);
        return normalized.equals("true_false") || normalized.equals("judge") || normalized.equals("judgment")
                || normalized.equals("3") || normalized.equals("4");
    }

    private boolean isFillBlank(String type) {
        return normalizedType(type).equals("fill_blank");
    }

    private boolean isSubjective(String type) {
        return switch (normalizedType(type)) {
            case "short_answer", "essay", "material_analysis", "calculation", "proof", "programming", "operation" -> true;
            default -> false;
        };
    }

    private boolean isStructuredSubjective(String type) {
        return switch (normalizedType(type)) {
            case "material_analysis", "calculation", "proof", "programming", "operation" -> true;
            default -> false;
        };
    }

    private String typeName(String type) {
        return switch (normalizedType(type)) {
            case "single_choice", "single", "1" -> "单项选择题";
            case "multiple_choice", "multiple", "2" -> "多项选择题";
            case "true_false", "judge", "judgment", "3", "4" -> "判断题";
            case "fill_blank" -> "填空题";
            case "short_answer" -> "简答题";
            case "essay" -> "论述题";
            case "material_analysis" -> "材料分析题";
            case "calculation" -> "计算题";
            case "proof" -> "证明题";
            case "programming" -> "编程题";
            case "operation" -> "操作题";
            case "matching" -> "匹配题";
            case "ordering" -> "排序题";
            case "cloze" -> "完形填空题";
            case "" -> "其他题型";
            default -> type;
        };
    }

    private record Option(String label, String originalKey, String text) {}

    private record SectionKey(Integer sectionOrder, String type) {}

    private record QuestionSection(String type, List<QuestionSnapshotVO> questions) {}
}
