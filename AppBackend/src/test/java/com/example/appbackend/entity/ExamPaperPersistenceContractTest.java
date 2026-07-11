package com.example.appbackend.entity;

import com.example.appbackend.dto.ExamPaperDTO;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamPaperPersistenceContractTest {

    @Test
    void paperContractContainsStableSnapshotAndLayoutFields() throws Exception {
        assertNotNull(ExamPaper.class.getDeclaredField("pageSize"));
        assertNotNull(ExamPaper.class.getDeclaredField("orientation"));
        assertNotNull(ExamPaper.class.getDeclaredField("columnsCount"));
        Map<String, Class<?>> layoutFields = Map.ofEntries(
                Map.entry("renderMode", ExamPaperDTO.PaperRenderMode.class),
                Map.entry("marginPreset", ExamPaperDTO.MarginPreset.class),
                Map.entry("customMarginTop", Integer.class),
                Map.entry("customMarginRight", Integer.class),
                Map.entry("customMarginBottom", Integer.class),
                Map.entry("customMarginLeft", Integer.class),
                Map.entry("columnSpace", Integer.class),
                Map.entry("hasBindingLine", Boolean.class),
                Map.entry("titleFontSize", Integer.class),
                Map.entry("subtitleFontSize", Integer.class),
                Map.entry("bodyFontSize", Integer.class));
        for (var entry : layoutFields.entrySet()) {
            Field field = ExamPaper.class.getDeclaredField(entry.getKey());
            assertEquals(entry.getValue(), field.getType());
            Column column = field.getAnnotation(Column.class);
            assertNotNull(column);
            if (!entry.getKey().startsWith("customMargin")) {
                assertTrue(!column.nullable(), entry.getKey() + " must be non-null");
            }
        }
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("bodyJson"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("answerJson"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("sortOrder"));
        assertTrue(!ExamPaper.class.getDeclaredField("createdBy").getAnnotation(Column.class).nullable());
    }

    @Test
    void paperQuestionContractEnforcesStableOrderAndLongTextSnapshots() throws Exception {
        Table table = ExamPaperQuestion.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertTrue(Arrays.stream(table.uniqueConstraints())
                .map(constraint -> List.of(constraint.columnNames()))
                .anyMatch(columns -> columns.equals(List.of("paper_id", "sort_order"))));
        assertTrue(Arrays.stream(table.uniqueConstraints())
                .map(constraint -> List.of(constraint.columnNames()))
                .anyMatch(columns -> columns.equals(List.of("paper_id", "question_id"))));

        assertLongText("bodyJson");
        assertLongText("answerJson");
        assertLongText("scoringJson");

        assertNotNull(ExamPaperQuestionRepository.class.getMethod(
                "findByPaperIdOrderBySortOrderAsc", Long.class));
        assertNotNull(ExamPaperQuestionRepository.class.getMethod(
                "findByPaperIdOrderBySortOrderAscIdAsc", Long.class));
    }

    @Test
    void paperContractIndexesCreatorStatusAndCreateTimeTogether() {
        Table table = ExamPaper.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertTrue(Arrays.stream(table.indexes())
                .anyMatch(index -> index.columnList().equals("created_by,status,create_time")));
    }

    @Test
    void createRequestKeepsTitleValidationLimits() throws Exception {
        Field title = ExamPaperDTO.CreateRequest.class.getDeclaredField("title");
        assertNotNull(title.getAnnotation(NotBlank.class));
        assertEquals(160, title.getAnnotation(Size.class).max());

        assertMinMax(ExamPaperDTO.CreateRequest.class.getDeclaredField("durationMinutes"), 1, 1440);
        Field layout = ExamPaperDTO.CreateRequest.class.getDeclaredField("layout");
        assertNotNull(layout.getAnnotation(NotNull.class));

        Field questions = ExamPaperDTO.CreateRequest.class.getDeclaredField("questions");
        assertNotNull(questions.getAnnotation(NotEmpty.class));

        Field score = ExamPaperDTO.SelectedQuestion.class.getDeclaredField("score");
        assertNotNull(score.getAnnotation(NotNull.class));
        assertEquals("0.01", score.getAnnotation(DecimalMin.class).value());

        Field sortOrder = ExamPaperDTO.SelectedQuestion.class.getDeclaredField("sortOrder");
        assertNotNull(sortOrder.getAnnotation(NotNull.class));
        assertEquals(1, sortOrder.getAnnotation(Min.class).value());

        Field columnsCount = ExamPaperDTO.PaperLayoutRequest.class.getDeclaredField("columnsCount");
        assertNotNull(columnsCount.getAnnotation(NotNull.class));

        assertNotNull(ExamPaperDTO.PaperVO.class.getDeclaredField("layout"));
    }

    private void assertLongText(String fieldName) throws Exception {
        Column column = ExamPaperQuestion.class.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertNotNull(column);
        assertTrue(column.columnDefinition().startsWith("LONGTEXT"));
    }

    private void assertMinMax(Field field, long min, long max) {
        assertEquals(min, field.getAnnotation(Min.class).value());
        assertEquals(max, field.getAnnotation(Max.class).value());
    }
}
