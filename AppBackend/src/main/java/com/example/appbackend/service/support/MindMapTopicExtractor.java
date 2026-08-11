package com.example.appbackend.service.support;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MindMapTopicExtractor {
    public static final int MAX_CENTER_TOPIC_LENGTH = 10;

    private static final Pattern EXTENSION = Pattern.compile("\\.(pdf|docx?|pptx?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEANINGFUL_TITLE = Pattern.compile("[A-Za-z0-9]*[\\u4e00-\\u9fa5A-Za-z0-9]{2,}(课程体系|学习路线|知识体系|复习提纲|项目拆解|课程|体系|路线|项目|知识|算法|架构|流程)");
    private static final List<String> NOISE_WORDS = List.of(
            "思维导图", "导图", "中心主题", "主题名称", "输入内容", "补充要求",
            "生成", "制作", "创建", "输出", "帮我", "请帮我", "请根据", "根据",
            "一份", "一个", "这个", "那个", "相关", "内容", "要求", "例如",
            "文件", "导入文件", "支持", "自动提取"
    );
    private static final Set<String> GENERIC_CANDIDATES = Set.of(
            "思维导图", "输入内容", "补充要求", "文件内容", "自动提取", "知识梳理",
            "课程体系", "复习提纲", "项目拆解", "核心知识", "主要内容"
    );
    private static final List<String> DOMAIN_HINTS = List.of(
            "课程", "体系", "学习", "路线", "知识", "项目", "复习", "提纲",
            "计算机", "数据结构", "操作系统", "网络", "Linux", "Python", "算法",
            "架构", "流程", "管理", "分析", "设计", "实践"
    );

    private MindMapTopicExtractor() {
    }

    public static String extract(String explicitCenterTopic, String userText, String sourceText, String fileName) {
        String explicit = trimTopic(cleanCandidate(explicitCenterTopic), MAX_CENTER_TOPIC_LENGTH);
        if (isUsefulTopic(explicit)) {
            return explicit;
        }

        List<Candidate> candidates = new ArrayList<>();
        String fullText = String.join("\n", nonNull(userText), nonNull(fileName), nonNull(sourceText));

        addCandidate(candidates, cleanFileName(fileName), 16, "filename");
        addTextCandidates(candidates, userText, 14, "user");
        addTextCandidates(candidates, sourceText, 10, "body");
        addFrequencyCandidates(candidates, fullText, 8);

        return candidates.stream()
                .map(candidate -> {
                    String value = trimTopic(candidate.value(), MAX_CENTER_TOPIC_LENGTH);
                    if (!isUsefulTopic(value)) {
                        return null;
                    }
                    return new Candidate(value, candidate.weight() + scoreTopic(value, fullText, candidate.source()), candidate.source());
                })
                .filter(candidate -> candidate != null)
                .max(Comparator.comparingInt(Candidate::weight).thenComparingInt(candidate -> candidate.value().length()))
                .map(Candidate::value)
                .orElse("");
    }

    public static String normalizeGeneratedTitle(String generatedTitle, String fallbackTitle) {
        String title = trimTopic(cleanCandidate(generatedTitle), 40);
        if (!isUsefulTopic(title) || GENERIC_CANDIDATES.contains(title)) {
            title = fallbackTitle;
        }
        if (!StringUtils.hasText(title)) {
            title = "思维导图";
        }
        return title.length() > 40 ? title.substring(0, 40) : title;
    }

    private static void addCandidate(List<Candidate> candidates, String value, int weight, String source) {
        String cleaned = cleanCandidate(value);
        if (StringUtils.hasText(cleaned)) {
            candidates.add(new Candidate(cleaned, weight, source));
        }
    }

    private static void addTextCandidates(List<Candidate> candidates, String text, int weight, String source) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String[] lines = text.replace("\r", "\n").split("\n");
        int usedLines = 0;
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int lineWeight = weight + Math.max(0, 5 - usedLines);
            List<String> phrases = splitPhrases(line);
            for (int index = 0; index < Math.min(phrases.size(), 6); index++) {
                addCandidate(candidates, phrases.get(index), lineWeight, source);
            }
            usedLines++;
            if (usedLines >= 16) {
                break;
            }
        }
    }

    private static void addFrequencyCandidates(List<Candidate> candidates, String text, int weight) {
        Map<String, Integer> counts = new HashMap<>();
        for (String phrase : splitPhrases(text)) {
            String value = trimTopic(cleanCandidate(phrase), MAX_CENTER_TOPIC_LENGTH);
            if (isUsefulTopic(value)) {
                counts.put(value, counts.getOrDefault(value, 0) + 1);
            }
        }
        counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .forEach(entry -> candidates.add(new Candidate(entry.getKey(), weight + entry.getValue() * 2, "frequency")));
    }

    private static List<String> splitPhrases(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.replaceAll("[【】「」『』《》]", " ");
        String[] parts = normalized.split("[\\n\\r\\t，。！？；：、,.!?;:|/\\\\()\\[\\]{}<>]+");
        List<String> phrases = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                phrases.add(part.trim());
            }
        }
        return phrases;
    }

    private static String cleanFileName(String name) {
        return EXTENSION.matcher(nonNull(name)).replaceFirst("").replaceAll("\\s+", " ");
    }

    private static String cleanCandidate(String value) {
        String text = EXTENSION.matcher(nonNull(value)).replaceFirst("")
                .replaceAll("https?://\\S+", " ")
                .replaceAll("[（(][^）)]{0,40}[）)]", " ")
                .replaceAll("\\b20\\d{6,}\\b", " ")
                .replaceAll("\\b\\d{6,}\\b", " ")
                .replaceAll("[A-Z]{2,}\\d{2,}", " ")
                .replaceAll("[_\\-—–]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        text = text.replaceFirst("^(请)?(帮我|根据|围绕|关于|生成|制作|创建|输出|整理|梳理|设计)+", "");
        text = text.replaceFirst("^(一份|一个|一下|有关|关于)+", "");
        for (String word : NOISE_WORDS) {
            text = text.replace(word, "");
        }
        return text.replaceAll("\\s+", "").trim();
    }

    private static String trimTopic(String value, int maxLength) {
        String text = nonNull(value).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = MEANINGFUL_TITLE.matcher(text);
        if (matcher.find()) {
            text = matcher.group();
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static boolean isUsefulTopic(String value) {
        String text = nonNull(value).trim();
        if (text.length() < 2) {
            return false;
        }
        if (GENERIC_CANDIDATES.contains(text)) {
            return false;
        }
        if (text.matches("^\\d+$") || text.matches("^[A-Za-z]{1,2}$")) {
            return false;
        }
        return text.matches(".*[\\u4e00-\\u9fa5A-Za-z].*");
    }

    private static int scoreTopic(String value, String fullText, String source) {
        String text = nonNull(value);
        int score = Math.min(text.length(), 10);
        score += Math.min(countOccurrences(fullText, text), 8) * 2;
        Set<String> matchedHints = new HashSet<>();
        for (String hint : DOMAIN_HINTS) {
            if (text.toLowerCase(Locale.ROOT).contains(hint.toLowerCase(Locale.ROOT))) {
                matchedHints.add(hint);
            }
        }
        score += matchedHints.size() * 3;
        if ("filename".equals(source) && text.matches(".*([\\d]{2,}|[\\u4e00-\\u9fa5]{2,4}(同学|老师|教授)).*")) {
            score -= 4;
        }
        if (text.matches("^(请|帮|生成|制作|根据).*")) {
            score -= 8;
        }
        return score;
    }

    private static int countOccurrences(String source, String target) {
        String text = nonNull(source);
        String word = nonNull(target);
        if (!StringUtils.hasText(word)) {
            return 0;
        }
        int count = 0;
        int index = text.indexOf(word);
        while (index >= 0) {
            count++;
            index = text.indexOf(word, index + word.length());
        }
        return count;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private record Candidate(String value, int weight, String source) {
    }
}
