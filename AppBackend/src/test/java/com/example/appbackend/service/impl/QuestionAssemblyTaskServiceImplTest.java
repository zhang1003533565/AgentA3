package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRule;
import com.example.appbackend.dto.QuestionAssemblyDTO.PrivateCommitResponse;
import com.example.appbackend.entity.ExamQuestionAssemblyTask;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionAssemblyTaskRepository;
import com.example.appbackend.service.AppMessageService;
import com.example.appbackend.service.QuestionAssemblyService;
import com.example.appbackend.service.QuestionGenerationMaterialParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAssemblyTaskServiceImplTest {

    @Mock ExamQuestionAssemblyTaskRepository taskRepository;
    @Mock QuestionAssemblyService questionAssemblyService;
    @Mock QuestionGenerationMaterialParser materialParser;
    @Mock AppMessageService appMessageService;

    private final AtomicReference<Runnable> queued = new AtomicReference<>();
    private QuestionAssemblyTaskServiceImpl service;
    private ExamQuestionAssemblyTask savedTask;

    @BeforeEach
    void setUp() {
        Executor executor = queued::set;
        service = new QuestionAssemblyTaskServiceImpl(
                taskRepository,
                questionAssemblyService,
                materialParser,
                appMessageService,
                new ObjectMapper(),
                executor);
    }

    @Test
    void submitReturnsQueuedTaskBeforeBackgroundGenerationRuns() {
        stubSubmission();
        var accepted = service.submit(request(), null, 7L, "Bearer token");

        assertEquals(ExamQuestionAssemblyTask.STATUS_QUEUED, accepted.getStatus());
        assertThat(accepted.getTaskId()).isNotBlank();
        assertThat(queued.get()).isNotNull();
        verify(questionAssemblyService, org.mockito.Mockito.never())
                .generate(any(), any(), any(), any());
    }

    @Test
    void backgroundCompletionSavesGeneratedQuestionsPrivatelyAndNotifiesOwner() {
        stubSubmission();
        service.submit(request(), null, 7L, "Bearer token");
        when(taskRepository.findById(101L)).thenAnswer(ignored -> Optional.of(savedTask));
        AssemblyResponse generated = new AssemblyResponse();
        generated.setDraftId("draft-1");
        generated.setGeneratedCount(2);
        generated.setQuestions(List.of());
        when(questionAssemblyService.generate(any(), eq(null), eq(7L), eq("Bearer token")))
                .thenReturn(generated);
        PrivateCommitResponse committed = new PrivateCommitResponse();
        committed.setImportedCount(2);
        committed.setQuestionIds(List.of(501L, 502L));
        when(questionAssemblyService.commitPrivate("draft-1", 7L)).thenReturn(committed);

        queued.get().run();

        assertEquals(ExamQuestionAssemblyTask.STATUS_SUCCEEDED, savedTask.getStatus());
        assertEquals(100, savedTask.getProgress());
        assertEquals(2, savedTask.getImportedCount());
        verify(questionAssemblyService).commitPrivate("draft-1", 7L);
        ArgumentCaptor<AppMessageDTO.CreateCommand> notification =
                ArgumentCaptor.forClass(AppMessageDTO.CreateCommand.class);
        verify(appMessageService).createIfAbsent(notification.capture());
        assertEquals(7L, notification.getValue().getUserId());
        assertEquals("EXAM", notification.getValue().getModuleType());
        assertEquals("QUESTION_ASSEMBLY_COMPLETED", notification.getValue().getEventType());
    }

    @Test
    void anotherUserCannotReadTaskByGuessingTaskId() {
        when(taskRepository.findByTaskIdAndUserId("task-secret", 8L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.get("task-secret", 8L));

        assertEquals(404, error.getCode());
    }

    private AssemblyRequest request() {
        AssemblyRule rule = new AssemblyRule();
        rule.setType("single_choice");
        rule.setDifficulty("easy");
        rule.setQuantity(2);
        AssemblyRequest request = new AssemblyRequest();
        request.setMode("generate");
        request.setBasisMode("text");
        request.setSourceType("text");
        request.setText("Java 并发");
        request.setSaveGeneratedToPrivate(true);
        request.setRules(List.of(rule));
        return request;
    }

    private void stubSubmission() {
        when(taskRepository.countByUserIdAndStatusIn(eq(7L), any())).thenReturn(0L);
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            savedTask = invocation.getArgument(0);
            if (savedTask.getId() == null) savedTask.setId(101L);
            return savedTask;
        });
    }
}
