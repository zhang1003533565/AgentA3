package com.example.appbackend.entity;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.repository.ExamPaperAttemptRepository;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AppExamPersistenceContractTest {

    @Test
    void attemptTableLocksIdentityActiveAttemptAndHistoryLookupContracts() throws Exception {
        Table table = ExamPaperAttempt.class.getAnnotation(Table.class);
        assertEquals("exam_paper_attempt", table.name());
        assertUnique(table, "paper_id", "user_id", "attempt_no");
        assertUnique(table, "paper_id", "user_id", "active_marker");
        assertTrue(Arrays.stream(table.indexes())
                .anyMatch(index -> index.columnList().equals("user_id,paper_id,status,started_at")));

        assertColumn(ExamPaperAttempt.class, "paperId", "paper_id", false);
        assertColumn(ExamPaperAttempt.class, "userId", "user_id", false);
        assertColumn(ExamPaperAttempt.class, "attemptNo", "attempt_no", false);
        Column activeMarker = assertColumn(ExamPaperAttempt.class, "activeMarker", "active_marker", true);
        assertEquals("TINYINT DEFAULT NULL", activeMarker.columnDefinition());

        Field status = ExamPaperAttempt.class.getDeclaredField("status");
        assertEquals(ExamPaperAttempt.Status.class, status.getType());
        assertEquals(EnumType.STRING, status.getAnnotation(Enumerated.class).value());
        assertEquals(Set.of("IN_PROGRESS", "SUBMITTED", "AUTO_SUBMITTED"),
                Arrays.stream(ExamPaperAttempt.Status.values()).map(Enum::name).collect(Collectors.toSet()));

        assertColumn(ExamPaperAttempt.class, "startedAt", "started_at", false);
        assertColumn(ExamPaperAttempt.class, "deadlineAt", "deadline_at", false);
        assertColumn(ExamPaperAttempt.class, "submittedAt", "submitted_at", true);
        assertColumn(ExamPaperAttempt.class, "createTime", "create_time", false);
        assertColumn(ExamPaperAttempt.class, "updateTime", "update_time", false);
        assertDecimal(ExamPaperAttempt.class, "objectiveScore", "objective_score", false, 10, 2,
                "DECIMAL(10,2) NOT NULL DEFAULT 0");
        assertDecimal(ExamPaperAttempt.class, "objectiveTotalScore", "objective_total_score", false, 10, 2,
                "DECIMAL(10,2) NOT NULL DEFAULT 0");
        assertEquals(BigDecimal.ZERO, new ExamPaperAttempt().getObjectiveScore());
        assertEquals(BigDecimal.ZERO, new ExamPaperAttempt().getObjectiveTotalScore());
        assertEquals(0, new ExamPaperAttempt().getAnsweredCount());
    }

    @Test
    void answerTableKeepsOneVersionedAnswerPerQuestionSnapshot() throws Exception {
        Table table = ExamPaperAttemptAnswer.class.getAnnotation(Table.class);
        assertEquals("exam_paper_attempt_answer", table.name());
        assertUnique(table, "attempt_id", "paper_question_id");
        assertFalse(Arrays.stream(table.indexes())
                .anyMatch(index -> index.columnList().equals("attempt_id")));

        assertColumn(ExamPaperAttemptAnswer.class, "attemptId", "attempt_id", false);
        assertColumn(ExamPaperAttemptAnswer.class, "paperQuestionId", "paper_question_id", false);
        Column answer = assertColumn(ExamPaperAttemptAnswer.class, "answerJson", "answer_json", false);
        assertTrue(answer.columnDefinition().startsWith("LONGTEXT"));
        assertNotNull(ExamPaperAttemptAnswer.class.getDeclaredField("version").getAnnotation(Version.class));
        Column answered = assertColumn(ExamPaperAttemptAnswer.class, "answered", "answered", false);
        assertEquals("BIT NOT NULL DEFAULT 0", answered.columnDefinition());
        assertFalse(new ExamPaperAttemptAnswer().getAnswered());
        assertColumn(ExamPaperAttemptAnswer.class, "correct", "correct", true);
        assertDecimal(ExamPaperAttemptAnswer.class, "score", "score", true, 10, 2, "");
        assertColumn(ExamPaperAttemptAnswer.class, "createTime", "create_time", false);
        assertColumn(ExamPaperAttemptAnswer.class, "updateTime", "update_time", false);
    }

    @Test
    void preSubmissionQuestionDtoCannotLeakAnswersOrScoringMaterial() {
        Set<String> safeFields = Arrays.stream(AppExamDTO.QuestionForAttempt.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertFalse(safeFields.contains("answerJson"));
        assertFalse(safeFields.contains("analysis"));
        assertFalse(safeFields.contains("scoringJson"));

        Set<String> resultFields = Arrays.stream(AppExamDTO.QuestionResult.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertTrue(resultFields.containsAll(List.of("answerJson", "analysis", "scoringJson", "userAnswerJson",
                "answered", "correct", "score")));
    }

    @Test
    void persistenceTypesMatchServiceBoundaryNeeds() throws Exception {
        assertEquals(Integer.class, ExamPaperAttempt.class.getDeclaredField("attemptNo").getType());
        assertEquals(Integer.class, ExamPaperAttempt.class.getDeclaredField("answeredCount").getType());
        assertEquals(Integer.class, ExamPaperAttempt.class.getDeclaredField("questionCount").getType());
        assertEquals(LocalDateTime.class, ExamPaperAttempt.class.getDeclaredField("deadlineAt").getType());
        assertEquals(Long.class, ExamPaperAttemptAnswer.class.getDeclaredField("version").getType());
    }

    @Test
    void activeMarkerIsOccupiedOnlyByInProgressAttempts() throws Exception {
        Method onCreate = ExamPaperAttempt.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);

        ExamPaperAttempt submitted = new ExamPaperAttempt();
        submitted.setStatus(ExamPaperAttempt.Status.SUBMITTED);
        onCreate.invoke(submitted);
        assertNull(submitted.getActiveMarker());

        ExamPaperAttempt inProgress = new ExamPaperAttempt();
        inProgress.setActiveMarker(null);
        onCreate.invoke(inProgress);
        assertEquals(1, inProgress.getActiveMarker());

        Method onUpdate = ExamPaperAttempt.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        inProgress.setStatus(ExamPaperAttempt.Status.SUBMITTED);
        onUpdate.invoke(inProgress);
        assertNull(inProgress.getActiveMarker());

        inProgress.setStatus(ExamPaperAttempt.Status.AUTO_SUBMITTED);
        onUpdate.invoke(inProgress);
        assertNull(inProgress.getActiveMarker());

        inProgress.setStatus(ExamPaperAttempt.Status.IN_PROGRESS);
        onUpdate.invoke(inProgress);
        assertEquals(1, inProgress.getActiveMarker());
    }

    @Test
    void attemptRepositoryExposesOnlyExplicitActiveAndCompletedQueries() throws Exception {
        Method active = ExamPaperAttemptRepository.class.getMethod(
                "findByPaperIdAndUserIdAndActiveMarker", Long.class, Long.class, Integer.class);
        assertEquals(Optional.class, active.getReturnType());

        Method history = ExamPaperAttemptRepository.class.getMethod(
                "findByPaperIdAndUserIdAndStatusInOrderBySubmittedAtDesc",
                Long.class, Long.class, Collection.class);
        assertEquals(List.class, history.getReturnType());
        assertNull(history.getAnnotation(Query.class));

        Method completedCount = ExamPaperAttemptRepository.class.getMethod(
                "countByPaperIdAndUserIdAndStatusIn", Long.class, Long.class, Collection.class);
        assertEquals(long.class, completedCount.getReturnType());

        assertThrows(NoSuchMethodException.class, () -> ExamPaperAttemptRepository.class.getMethod(
                "findByPaperIdAndUserIdAndStatus", Long.class, Long.class, ExamPaperAttempt.Status.class));
        assertThrows(NoSuchMethodException.class, () -> ExamPaperAttemptRepository.class.getMethod(
                "findByPaperIdAndUserIdOrderByAttemptNoDesc", Long.class, Long.class));
        assertThrows(NoSuchMethodException.class, () -> ExamPaperAttemptRepository.class.getMethod(
                "countByPaperIdAndUserId", Long.class, Long.class));
    }

    private Column assertColumn(Class<?> type, String fieldName, String columnName, boolean nullable) throws Exception {
        Column column = type.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertNotNull(column, fieldName);
        assertEquals(columnName, column.name(), fieldName);
        assertEquals(nullable, column.nullable(), fieldName);
        return column;
    }

    private void assertDecimal(Class<?> type, String fieldName, String columnName, boolean nullable,
                               int precision, int scale, String definition) throws Exception {
        Column column = assertColumn(type, fieldName, columnName, nullable);
        assertEquals(precision, column.precision());
        assertEquals(scale, column.scale());
        assertEquals(definition, column.columnDefinition());
    }

    private void assertUnique(Table table, String... columns) {
        assertTrue(Arrays.stream(table.uniqueConstraints())
                .anyMatch(constraint -> Arrays.equals(columns, constraint.columnNames())),
                "missing unique constraint " + Arrays.toString(columns));
    }
}
