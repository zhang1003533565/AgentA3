package com.example.appbackend.service.impl;

import com.example.appbackend.dto.KnowledgeGraphDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.service.LearningPathService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonKnowledgeGraphServiceImplTest {

    @Test
    void overlaysRealMasteryAndActivePathWithoutInventingDynamicScores() {
        LearningPathService learningPathService = mock(LearningPathService.class);
        LearningPathDTO.HomeView home = new LearningPathDTO.HomeView();
        home.setMastery(List.of(
                mastery("python.expression.arithmetic", "算术表达式", "85.00", "mastered", 4),
                mastery("python.data_type.collection", "集合类型", "42.00", "weak", 3),
                mastery("python.lists.slicing", "列表切片", "0.00", "new", 0)
        ));
        LearningPathDTO.PathView path = new LearningPathDTO.PathView();
        LearningPathDTO.PathItemView item = new LearningPathDTO.PathItemView();
        item.setId(91L);
        item.setKnowledgePoint("python.data_type.collection");
        item.setObjective("巩固集合类型选择");
        path.setItems(List.of(item));
        home.setActivePath(path);
        when(learningPathService.getHome(7L, "python")).thenReturn(home);

        KnowledgeGraphDTO.GraphView graph =
                new PythonKnowledgeGraphServiceImpl(learningPathService).getGraph(7L);

        assertThat(graph.getCourseKey()).isEqualTo("python");
        assertThat(graph.getNodes()).extracting(KnowledgeGraphDTO.NodeView::getId)
                .contains("python.expression.arithmetic", "python.data_type.collection",
                        "python.lists.slicing");
        KnowledgeGraphDTO.NodeView collection = graph.getNodes().stream()
                .filter(node -> "python.data_type.collection".equals(node.getId()))
                .findFirst().orElseThrow();
        assertThat(collection.getStatus()).isEqualTo("weak");
        assertThat(collection.getScore()).isEqualByComparingTo("42.00");
        assertThat(collection.getOnActivePath()).isTrue();
        assertThat(collection.getPathItemId()).isEqualTo(91L);

        KnowledgeGraphDTO.NodeView slicing = graph.getNodes().stream()
                .filter(node -> "python.lists.slicing".equals(node.getId()))
                .findFirst().orElseThrow();
        assertThat(slicing.getTitle()).isEqualTo("列表切片");
        assertThat(slicing.getScore()).isEqualByComparingTo("0.00");
        assertThat(slicing.getStatus()).isEqualTo("available");

        assertThat(graph.getEdges()).anySatisfy(edge -> {
            assertThat(edge.getSource()).isEqualTo("python.expression.arithmetic");
            assertThat(edge.getTarget()).isEqualTo("python.data_type.collection");
        });
        assertThat(graph.getSummary().getMastered()).isEqualTo(1);
        assertThat(graph.getSummary().getWeak()).isEqualTo(1);
    }

    @Test
    void locksCatalogNodesUntilTheirRecordedPrerequisitesReachUnlockScore() {
        LearningPathService learningPathService = mock(LearningPathService.class);
        LearningPathDTO.HomeView home = new LearningPathDTO.HomeView();
        home.setMastery(List.of());
        when(learningPathService.getHome(9L, "python")).thenReturn(home);

        KnowledgeGraphDTO.GraphView graph =
                new PythonKnowledgeGraphServiceImpl(learningPathService).getGraph(9L);

        assertThat(node(graph, "python.expression.arithmetic").getStatus()).isEqualTo("available");
        assertThat(node(graph, "python.data_type.collection").getStatus()).isEqualTo("locked");
        assertThat(graph.getSummary().getLocked()).isGreaterThan(0);
    }

    private LearningPathDTO.MasteryView mastery(
            String key, String name, String score, String status, int attempts) {
        LearningPathDTO.MasteryView mastery = new LearningPathDTO.MasteryView();
        mastery.setKnowledgePointKey(key);
        mastery.setKnowledgePointName(name);
        mastery.setScore(new BigDecimal(score));
        mastery.setStatus(status);
        mastery.setAttemptCount(attempts);
        mastery.setCorrectCount(Math.max(0, attempts - 1));
        mastery.setWrongCount(attempts > 0 ? 1 : 0);
        mastery.setNextReviewAt(LocalDateTime.now().plusDays(1));
        return mastery;
    }

    private KnowledgeGraphDTO.NodeView node(KnowledgeGraphDTO.GraphView graph, String id) {
        return graph.getNodes().stream()
                .filter(node -> id.equals(node.getId()))
                .findFirst().orElseThrow();
    }
}
