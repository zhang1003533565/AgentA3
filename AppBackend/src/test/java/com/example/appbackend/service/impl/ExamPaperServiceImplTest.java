package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.dto.ExamPaperDTO.RandomPreviewRequest;
import com.example.appbackend.dto.ExamPaperDTO.RandomRule;
import com.example.appbackend.dto.ExamPaperDTO.SelectedQuestion;
import com.example.appbackend.dto.ExamPaperDTO.SelectionMode;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.ExamPaperDocumentGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPaperServiceImplTest {

    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private ExamPaperRepository paperRepository;
    @Mock
    private ExamPaperQuestionRepository paperQuestionRepository;
    @Mock
    private RandomGenerator randomGenerator;
    @Mock
    private ExamPaperDocumentGenerator documentGenerator;

    private ExamPaperServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExamPaperServiceImpl(
                questionRepository,
                paperRepository,
                paperQuestionRepository,
                randomGenerator,
                documentGenerator);
    }

    @Test
    void randomPreviewSelectsWithoutReplacement() {
        when(questionRepository.findActiveCandidates("single_choice", "easy"))
                .thenReturn(List.of(question(1L), question(2L), question(3L)));

        var result = service.randomPreview(preview("single_choice", "easy", 2));

        assertEquals(2, result.getQuestions().size());
        assertEquals(2, result.getQuestions().stream()
                .map(QuestionSnapshotVO::getQuestionId)
                .distinct()
                .count());
    }

    @Test
    void randomPreviewRejectsInsufficientCandidates() {
        when(questionRepository.findActiveCandidates("single_choice", "easy"))
                .thenReturn(List.of(question(1L)));

        assertThrows(BusinessException.class,
                () -> service.randomPreview(preview("single_choice", "easy", 2)));
    }

    @Test
    void randomPreviewDoesNotRepeatCandidatesAcrossOverlappingRules() {
        when(questionRepository.findActiveCandidates("single_choice", "easy"))
                .thenReturn(List.of(question(1L), question(2L)))
                .thenReturn(List.of(question(1L), question(2L)));
        RandomPreviewRequest request = new RandomPreviewRequest();
        request.setRules(List.of(
                rule("single_choice", "easy", 1),
                rule("single_choice", "easy", 1)));

        var result = service.randomPreview(request);

        assertEquals(2, result.getQuestions().stream()
                .map(QuestionSnapshotVO::getQuestionId)
                .distinct()
                .count());
        assertEquals(List.of(1, 2), result.getQuestions().stream()
                .map(QuestionSnapshotVO::getSortOrder)
                .toList());
    }

    @Test
    void randomPreviewFindsFeasibleAllocationWhenBroadRulePrecedesNarrowRule() {
        ExamQuestion easy = question(1L);
        ExamQuestion hard = question(2L);
        hard.setDifficulty("hard");
        when(questionRepository.findActiveCandidates("single_choice", null))
                .thenReturn(List.of(easy, hard));
        when(questionRepository.findActiveCandidates("single_choice", "easy"))
                .thenReturn(List.of(easy));
        when(randomGenerator.nextInt(2)).thenReturn(1);
        RandomPreviewRequest request = new RandomPreviewRequest();
        request.setRules(List.of(
                rule("single_choice", null, 1),
                rule("single_choice", "easy", 1)));

        var result = service.randomPreview(request);

        assertEquals(List.of(2L, 1L), result.getQuestions().stream()
                .map(QuestionSnapshotVO::getQuestionId)
                .toList());
        assertEquals(List.of(1, 2), result.getQuestions().stream()
                .map(QuestionSnapshotVO::getSortOrder)
                .toList());
    }

    @Test
    void createRejectsDuplicateQuestionIdsBeforeLoadingQuestions() {
        CreateRequest request = createRequest(
                selected(1L, "5.00", 1),
                selected(1L, "7.50", 2));

        assertThrows(BusinessException.class, () -> service.create(request, 9L));

        verify(questionRepository, never()).findAllById(any());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateSortOrdersBeforeLoadingQuestions() {
        CreateRequest request = createRequest(
                selected(1L, "5.00", 1),
                selected(2L, "7.50", 1));

        assertThrows(BusinessException.class, () -> service.create(request, 9L));

        verify(questionRepository, never()).findAllById(any());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingQuestionIds() {
        CreateRequest request = createRequest(
                selected(1L, "5.00", 1),
                selected(2L, "7.50", 2));
        when(questionRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(question(1L)));

        assertThrows(BusinessException.class, () -> service.create(request, 9L));

        verify(paperRepository, never()).save(any());
    }

    @Test
    void createRejectsInactiveQuestionIds() {
        CreateRequest request = createRequest(selected(1L, "5.00", 1));
        ExamQuestion inactive = question(1L);
        inactive.setStatus(0);
        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(inactive));

        assertThrows(BusinessException.class, () -> service.create(request, 9L));

        verify(paperRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createUsesScoreOverridesCalculatesTotalAndCopiesSnapshotsInStableOrder() {
        CreateRequest request = createRequest(
                selected(2L, "7.50", 2),
                selected(1L, "5.00", 1));
        ExamQuestion first = question(1L);
        first.setStem("original stem");
        first.setBodyJson("original body");
        first.setAnswerJson("original answer");
        first.setAnalysis("original analysis");
        first.setScoringJson("original scoring");
        ExamQuestion second = question(2L);
        when(questionRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(first, second));
        when(paperRepository.save(any())).thenAnswer(invocation -> {
            ExamPaper paper = invocation.getArgument(0);
            paper.setId(88L);
            return paper;
        });
        when(paperQuestionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(request, 9L);

        var paperCaptor = org.mockito.ArgumentCaptor.forClass(ExamPaper.class);
        verify(paperRepository).save(paperCaptor.capture());
        ExamPaper savedPaper = paperCaptor.getValue();
        assertEquals(new BigDecimal("12.50"), savedPaper.getTotalScore());
        assertEquals(2, savedPaper.getQuestionCount());
        assertEquals(9L, savedPaper.getCreatedBy());

        var snapshotsCaptor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(paperQuestionRepository).saveAll(snapshotsCaptor.capture());
        List<ExamPaperQuestion> savedSnapshots = new java.util.ArrayList<>();
        snapshotsCaptor.getValue().forEach(item -> savedSnapshots.add((ExamPaperQuestion) item));
        assertEquals(List.of(1, 2), savedSnapshots.stream().map(ExamPaperQuestion::getSortOrder).toList());
        assertEquals("original body", savedSnapshots.get(0).getBodyJson());
        assertEquals("original answer", savedSnapshots.get(0).getAnswerJson());
        assertEquals(new BigDecimal("5.00"), savedSnapshots.get(0).getScore());
        assertEquals(1, savedSnapshots.get(0).getSortOrder());
        assertEquals(List.of(1, 1), savedSnapshots.stream().map(ExamPaperQuestion::getSectionOrder).toList());
        assertEquals(new BigDecimal("12.50"), result.getTotalScore());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAssignsSharedSectionOrderByTypeFirstAppearance() {
        CreateRequest request = createRequest(
                selected(1L, "5.00", 1),
                selected(2L, "5.00", 2),
                selected(3L, "5.00", 3));
        ExamQuestion first = question(1L);
        ExamQuestion second = question(2L);
        second.setType("true_false");
        ExamQuestion third = question(3L);
        when(questionRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(first, second, third));
        when(paperRepository.save(any())).thenAnswer(invocation -> {
            ExamPaper paper = invocation.getArgument(0);
            paper.setId(88L);
            return paper;
        });
        when(paperQuestionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, 9L);

        var captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(paperQuestionRepository).saveAll(captor.capture());
        List<ExamPaperQuestion> snapshots = new java.util.ArrayList<>();
        captor.getValue().forEach(item -> snapshots.add((ExamPaperQuestion) item));
        assertEquals(List.of(1, 2, 1), snapshots.stream().map(ExamPaperQuestion::getSectionOrder).toList());
    }

    @Test
    void listFiltersByCreatorStatusAndTitleKeyword() {
        when(paperRepository.findByCreatedByAndStatusAndTitleContainingOrderByCreateTimeDesc(
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("期末"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(2, 20, "期末", 9L);

        verify(paperRepository).findByCreatedByAndStatusAndTitleContainingOrderByCreateTimeDesc(
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("期末"), any());
    }

    @Test
    void detailRejectsAccessByDifferentCreator() {
        ExamPaper paper = new ExamPaper();
        paper.setId(3L);
        paper.setCreatedBy(10L);
        paper.setStatus(1);
        when(paperRepository.findById(3L)).thenReturn(Optional.of(paper));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.detail(3L, 11L));

        assertEquals(Result.FORBIDDEN_CODE, error.getCode());
        assertEquals("无权访问该试卷", error.getMessage());
        verify(paperQuestionRepository, never()).findByPaperIdOrderBySortOrderAscIdAsc(any());
    }

    @Test
    void downloadDoesNotGenerateWhenCreatorCheckRejectsAccess() {
        ExamPaper paper = paper(3L, 10L, "期末考试");
        when(paperRepository.findById(3L)).thenReturn(Optional.of(paper));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.download(3L, 11L, DownloadContent.PAPER));

        assertEquals(Result.FORBIDDEN_CODE, error.getCode());
        verifyNoInteractions(documentGenerator);
        verify(paperQuestionRepository, never()).findByPaperIdOrderBySortOrderAscIdAsc(any());
    }

    @Test
    void downloadGeneratesFromAuthorizedDetailSnapshotWithoutPersisting() {
        ExamPaper paper = paper(3L, 10L, "期末考试");
        ExamPaperQuestion snapshot = new ExamPaperQuestion();
        snapshot.setId(30L);
        snapshot.setPaperId(3L);
        snapshot.setQuestionId(8L);
        snapshot.setSortOrder(1);
        snapshot.setScore(new BigDecimal("5.00"));
        snapshot.setStem("快照题干");
        when(paperRepository.findById(3L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(3L))
                .thenReturn(List.of(snapshot));
        when(documentGenerator.generate(any(), org.mockito.ArgumentMatchers.eq(DownloadContent.ANSWER)))
                .thenReturn(new byte[]{1, 2, 3});

        var file = service.download(3L, 10L, DownloadContent.ANSWER);

        assertEquals("期末考试", file.title());
        assertEquals(3, file.bytes().length);
        var paperCaptor = org.mockito.ArgumentCaptor.forClass(
                com.example.appbackend.dto.ExamPaperDTO.PaperVO.class);
        verify(documentGenerator).generate(paperCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(DownloadContent.ANSWER));
        assertEquals("快照题干", paperCaptor.getValue().getQuestions().getFirst().getStem());
        verify(paperRepository, never()).save(any());
        verify(paperQuestionRepository, never()).saveAll(any());
    }

    private RandomPreviewRequest preview(String type, String difficulty, int quantity) {
        RandomPreviewRequest request = new RandomPreviewRequest();
        request.setRules(List.of(rule(type, difficulty, quantity)));
        return request;
    }

    private RandomRule rule(String type, String difficulty, int quantity) {
        RandomRule rule = new RandomRule();
        rule.setType(type);
        rule.setDifficulty(difficulty);
        rule.setQuantity(quantity);
        return rule;
    }

    private CreateRequest createRequest(SelectedQuestion... questions) {
        CreateRequest request = new CreateRequest();
        request.setTitle("paper");
        request.setPageSize(PageSize.A4);
        request.setOrientation(Orientation.PORTRAIT);
        request.setColumnsCount(1);
        request.setSelectionMode(SelectionMode.MANUAL);
        request.setQuestions(List.of(questions));
        return request;
    }

    private SelectedQuestion selected(Long id, String score, int sortOrder) {
        SelectedQuestion selected = new SelectedQuestion();
        selected.setQuestionId(id);
        selected.setScore(new BigDecimal(score));
        selected.setSortOrder(sortOrder);
        return selected;
    }

    private ExamQuestion question(Long id) {
        ExamQuestion question = new ExamQuestion();
        question.setId(id);
        question.setType("single_choice");
        question.setDifficulty("easy");
        question.setStem("question " + id);
        question.setScore(new BigDecimal("5.00"));
        question.setBodyJson("{\"options\":[]}");
        question.setAnswerJson("{\"value\":\"A\"}");
        question.setScoringJson("{\"mode\":\"exact\"}");
        question.setRawQuestionJson("{\"id\":\"q" + id + "\"}");
        question.setStatus(1);
        return question;
    }

    private ExamPaper paper(Long id, Long createdBy, String title) {
        ExamPaper paper = new ExamPaper();
        paper.setId(id);
        paper.setCreatedBy(createdBy);
        paper.setTitle(title);
        paper.setStatus(1);
        return paper;
    }
}
