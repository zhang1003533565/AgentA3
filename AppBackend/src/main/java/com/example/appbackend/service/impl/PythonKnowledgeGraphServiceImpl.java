package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeGraphDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.PythonKnowledgeGraphService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PythonKnowledgeGraphServiceImpl implements PythonKnowledgeGraphService {
    private static final String PYTHON = "python";
    private static final BigDecimal UNLOCK_SCORE = new BigDecimal("60");

    /*
     * These nodes are the knowledge points already used by
     * StudentPracticeExamInitializer. The graph adds ordering metadata only; it
     * does not introduce synthetic learning evidence or mastery scores.
     */
    private static final List<CatalogNode> CATALOG = List.of(
            node("python.expression.arithmetic", "算术表达式", "表达式与基础运算", "基础语法", 0, 0),
            node("python.data_type.collection", "集合类型", "list、dict、set 等集合类型", "数据类型", 1, 0,
                    "python.expression.arithmetic"),
            node("python.data_type.sequence", "序列类型", "列表、元组及可变性", "数据类型", 1, 1,
                    "python.expression.arithmetic"),
            node("python.function.syntax", "函数定义", "def、参数与返回值", "函数", 2, 0,
                    "python.data_type.sequence"),
            node("python.exception.application", "异常处理实践", "try、except 与 ValueError", "异常处理", 3, 0,
                    "python.function.syntax"),
            node("python.exception.reliability", "异常与可靠性", "恢复、提示、日志与精确捕获", "异常处理", 3, 1,
                    "python.exception.application"),
            node("python.algorithm.complexity", "算法复杂度", "循环次数与时间复杂度分析", "算法", 4, 0,
                    "python.function.syntax")
    );

    private final LearningPathService learningPathService;

    public PythonKnowledgeGraphServiceImpl(LearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    @Override
    public KnowledgeGraphDTO.GraphView getGraph(Long userId) {
        LearningPathDTO.HomeView home = learningPathService.getHome(userId, PYTHON);
        Map<String, LearningPathDTO.MasteryView> masteryByKey = safe(home.getMastery()).stream()
                .filter(item -> StringUtils.hasText(item.getKnowledgePointKey()))
                .collect(Collectors.toMap(
                        item -> item.getKnowledgePointKey().trim(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, LearningPathDTO.PathItemView> pathByKey = activePathItems(home).stream()
                .filter(item -> StringUtils.hasText(item.getKnowledgePoint()))
                .collect(Collectors.toMap(
                        item -> item.getKnowledgePoint().trim(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, CatalogNode> catalogById = CATALOG.stream()
                .collect(Collectors.toMap(CatalogNode::id, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Set<String> allIds = new LinkedHashSet<>(catalogById.keySet());
        allIds.addAll(masteryByKey.keySet());
        allIds.addAll(pathByKey.keySet());

        List<KnowledgeGraphDTO.NodeView> nodes = new ArrayList<>();
        for (String id : allIds) {
            CatalogNode catalog = catalogById.get(id);
            LearningPathDTO.MasteryView mastery = masteryByKey.get(id);
            LearningPathDTO.PathItemView pathItem = pathByKey.get(id);
            nodes.add(toNode(id, catalog, mastery, pathItem, masteryByKey));
        }
        nodes.sort(Comparator.comparing(KnowledgeGraphDTO.NodeView::getLevel)
                .thenComparing(KnowledgeGraphDTO.NodeView::getOrder)
                .thenComparing(KnowledgeGraphDTO.NodeView::getId));

        List<KnowledgeGraphDTO.EdgeView> edges = CATALOG.stream()
                .flatMap(target -> target.prerequisites().stream()
                        .map(source -> edge(source, target.id())))
                .toList();

        KnowledgeGraphDTO.GraphView graph = new KnowledgeGraphDTO.GraphView();
        graph.setCourseKey(PYTHON);
        graph.setNodes(nodes);
        graph.setEdges(edges);
        graph.setSummary(summary(nodes));
        return graph;
    }

    private KnowledgeGraphDTO.NodeView toNode(
            String id,
            CatalogNode catalog,
            LearningPathDTO.MasteryView mastery,
            LearningPathDTO.PathItemView pathItem,
            Map<String, LearningPathDTO.MasteryView> masteryByKey) {
        List<String> prerequisites = catalog == null ? List.of() : catalog.prerequisites();
        KnowledgeGraphDTO.NodeView node = new KnowledgeGraphDTO.NodeView();
        node.setId(id);
        node.setTitle(title(id, catalog, mastery));
        node.setDescription(catalog == null ? null : catalog.description());
        node.setGroup(catalog == null ? groupFromKey(id) : catalog.group());
        node.setLevel(catalog == null ? 5 : catalog.level());
        node.setOrder(catalog == null ? 0 : catalog.order());
        node.setPrerequisiteIds(prerequisites);
        node.setStatus(status(mastery, prerequisites, masteryByKey));
        node.setScore(mastery == null ? BigDecimal.ZERO.setScale(2) : mastery.getScore());
        node.setAttemptCount(mastery == null ? 0 : mastery.getAttemptCount());
        node.setCorrectCount(mastery == null ? 0 : mastery.getCorrectCount());
        node.setWrongCount(mastery == null ? 0 : mastery.getWrongCount());
        node.setNextReviewAt(mastery == null ? null : mastery.getNextReviewAt());
        node.setOnActivePath(pathItem != null);
        node.setPathItemId(pathItem == null ? null : pathItem.getId());
        node.setPathObjective(pathItem == null ? null : pathItem.getObjective());
        return node;
    }

    private String status(
            LearningPathDTO.MasteryView mastery,
            List<String> prerequisites,
            Map<String, LearningPathDTO.MasteryView> masteryByKey) {
        if (mastery != null && StringUtils.hasText(mastery.getStatus())
                && !"new".equals(mastery.getStatus())) {
            return mastery.getStatus();
        }
        boolean unlocked = prerequisites.stream().allMatch(id -> {
            LearningPathDTO.MasteryView prerequisite = masteryByKey.get(id);
            return prerequisite != null && prerequisite.getScore() != null
                    && prerequisite.getScore().compareTo(UNLOCK_SCORE) >= 0;
        });
        return prerequisites.isEmpty() || unlocked ? "available" : "locked";
    }

    private String title(String id, CatalogNode catalog, LearningPathDTO.MasteryView mastery) {
        if (mastery != null && StringUtils.hasText(mastery.getKnowledgePointName())) {
            return mastery.getKnowledgePointName().trim();
        }
        if (catalog != null) {
            return catalog.title();
        }
        String tail = id.substring(id.lastIndexOf('.') + 1).replace('_', ' ').trim();
        return tail.isEmpty() ? id : tail;
    }

    private String groupFromKey(String id) {
        String[] parts = id.split("\\.");
        if (parts.length < 2) {
            return "其他";
        }
        return parts[1].replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private List<LearningPathDTO.PathItemView> activePathItems(LearningPathDTO.HomeView home) {
        return home.getActivePath() == null ? List.of() : safe(home.getActivePath().getItems());
    }

    private KnowledgeGraphDTO.Summary summary(List<KnowledgeGraphDTO.NodeView> nodes) {
        KnowledgeGraphDTO.Summary summary = new KnowledgeGraphDTO.Summary();
        summary.setTotal(nodes.size());
        summary.setMastered(count(nodes, "mastered"));
        summary.setLearning(count(nodes, "learning"));
        summary.setWeak(count(nodes, "weak"));
        summary.setAvailable(count(nodes, "available"));
        summary.setLocked(count(nodes, "locked"));
        LocalDateTime now = LocalDateTime.now();
        summary.setDueForReview((int) nodes.stream()
                .filter(node -> node.getNextReviewAt() != null
                        && !node.getNextReviewAt().isAfter(now))
                .count());
        return summary;
    }

    private int count(List<KnowledgeGraphDTO.NodeView> nodes, String status) {
        return (int) nodes.stream().filter(node -> status.equals(node.getStatus())).count();
    }

    private static CatalogNode node(String id, String title, String description,
                                    String group, int level, int order, String... prerequisites) {
        return new CatalogNode(id, title, description, group, level, order, List.of(prerequisites));
    }

    private static KnowledgeGraphDTO.EdgeView edge(String source, String target) {
        KnowledgeGraphDTO.EdgeView edge = new KnowledgeGraphDTO.EdgeView();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setRelation("prerequisite");
        return edge;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record CatalogNode(
            String id,
            String title,
            String description,
            String group,
            int level,
            int order,
            List<String> prerequisites) {
    }
}
