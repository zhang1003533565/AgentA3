package com.example.appbackend.entity;

import com.example.appbackend.dto.ExamPaperDTO;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamPaperPersistenceContractTest {

    @Test
    void paperContractContainsStableSnapshotAndLayoutFields() throws Exception {
        assertNotNull(ExamPaper.class.getDeclaredField("pageSize"));
        assertNotNull(ExamPaper.class.getDeclaredField("orientation"));
        assertNotNull(ExamPaper.class.getDeclaredField("columnsCount"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("bodyJson"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("answerJson"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("sortOrder"));
    }

    @Test
    void paperQuestionContractEnforcesStableOrderAndLongTextSnapshots() throws Exception {
        Table table = ExamPaperQuestion.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertTrue(Arrays.stream(table.uniqueConstraints())
                .map(constraint -> List.of(constraint.columnNames()))
                .anyMatch(columns -> columns.equals(List.of("paper_id", "sort_order"))));

        assertLongText("bodyJson");
        assertLongText("answerJson");
        assertLongText("scoringJson");

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
    }

    private void assertLongText(String fieldName) throws Exception {
        Column column = ExamPaperQuestion.class.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertNotNull(column);
        assertTrue(column.columnDefinition().startsWith("LONGTEXT"));
    }
}
