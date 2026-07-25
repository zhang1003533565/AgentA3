package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuestionServiceVisibilityTest {

    @Mock ExamQuestionRepository repository;
    private ExamQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(100);
        when(repository.save(any())).thenAnswer(invocation -> {
            ExamQuestion saved = invocation.getArgument(0);
            saved.setId(ids.incrementAndGet());
            return saved;
        });
        service = new ExamQuestionServiceImpl(repository, new ObjectMapper());
    }

    @Test
    void userImportIsAlwaysPrivateAndOwnedByCurrentUser() {
        service.importQuestions(request(), "true_false", 7L);

        ExamQuestion saved = savedQuestion();
        assertEquals(ExamQuestion.VISIBILITY_PRIVATE, saved.getVisibility());
        assertEquals(7L, saved.getOwnerUserId());
        assertEquals(7L, saved.getCreatedBy());
    }

    @Test
    void explicitAdminImportCreatesPublicQuestionWithoutPrivateOwner() {
        service.importPublicQuestions(request(), "true_false", 1L);

        ExamQuestion saved = savedQuestion();
        assertEquals(ExamQuestion.VISIBILITY_PUBLIC, saved.getVisibility());
        assertNull(saved.getOwnerUserId());
        assertEquals(1L, saved.getCreatedBy());
    }

    private ExamQuestion savedQuestion() {
        ArgumentCaptor<ExamQuestion> captor = ArgumentCaptor.forClass(ExamQuestion.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private ExamQuestionDTO.ImportRequest request() {
        ExamQuestionDTO.ImportRequest request = new ExamQuestionDTO.ImportRequest();
        request.setSourceAgent("question_bank_orchestrator_agent");
        request.setSourceScene("user_question_generation");
        request.setQuestions(List.of(Map.ofEntries(
                Map.entry("id", "tf-1"),
                Map.entry("type", "true_false"),
                Map.entry("stem", "线程启动后可以再次调用 start。"),
                Map.entry("score", 2),
                Map.entry("difficulty", "easy"),
                Map.entry("knowledgePoints", List.of("线程生命周期")),
                Map.entry("tags", List.of("Java")),
                Map.entry("body", Map.of("statement", "线程启动后可以再次调用 start。")),
                Map.entry("answer", Map.of("correct", false)),
                Map.entry("analysis", "线程对象只能启动一次。"),
                Map.entry("scoring", Map.of(
                        "mode", "exact",
                        "rubrics", List.of(Map.of("criterion", "判断正确", "score", 2)))),
                Map.entry("sourceBasis", List.of("Java 线程生命周期"))
        )));
        return request;
    }
}
