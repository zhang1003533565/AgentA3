package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode;
import com.example.appbackend.dto.ExamPaperDTO.SelectionMode;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppExamPdfServiceTest {
    @Mock ExamPaperRepository paperRepository;
    @Mock ExamPaperQuestionRepository questionRepository;
    @Mock ExamPaperDocumentDispatcher dispatcher;
    @Mock LibreOfficePreviewConverter converter;

    private AppExamPdfService service;

    @BeforeEach
    void setUp() {
        service = new AppExamPdfService(paperRepository, questionRepository, dispatcher, converter,
                Path.of(System.getProperty("java.io.tmpdir"), "app-exam-pdf-test"));
    }

    @Test
    void publishedPaperUsesPersistedSnapshotAndAlwaysGeneratesBlankPdf() {
        ExamPaper paper = paper();
        ExamPaperQuestion snapshot = question();
        when(paperRepository.findByIdAndStatusAndPublishedTrue(7L, 1)).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(snapshot));
        when(dispatcher.generate(any(), eq(DownloadContent.PAPER), any())).thenReturn(new byte[]{1, 2});
        when(converter.convert(any(), any())).thenReturn(
                new LibreOfficePreviewConverter.ConversionResult(new byte[]{3, 4}, 1));

        AppExamPdfService.PdfFile result = service.downloadBlankPaper(7L, 9L);

        var paperCaptor = ArgumentCaptor.forClass(com.example.appbackend.dto.ExamPaperDTO.PaperVO.class);
        verify(dispatcher).generate(paperCaptor.capture(), eq(DownloadContent.PAPER), any());
        assertEquals("快照题干", paperCaptor.getValue().getQuestions().getFirst().getStem());
        assertEquals("标准答案", paperCaptor.getValue().getQuestions().getFirst().getAnswerJson());
        assertArrayEquals(new byte[]{3, 4}, result.bytes());
        assertEquals("发布试卷", result.title());
        verify(converter).deleteRecursively(any());
    }

    @Test
    void unavailableOrUnpublishedPaperIsInvisibleAndDoesNotGenerate() {
        when(paperRepository.findByIdAndStatusAndPublishedTrue(7L, 1)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.downloadBlankPaper(7L, 9L));

        assertEquals(404, error.getCode());
        verifyNoInteractions(questionRepository, dispatcher, converter);
    }

    @Test
    void anonymousRequestIsRejectedBeforeRepositoryAccess() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.downloadBlankPaper(7L, null));
        assertEquals(401, error.getCode());
        verifyNoInteractions(paperRepository, questionRepository, dispatcher, converter);
    }

    @Test
    void emptyPublishedPaperIsRejectedBeforeDocumentGeneration() {
        when(paperRepository.findByIdAndStatusAndPublishedTrue(7L, 1)).thenReturn(Optional.of(paper()));
        when(questionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.downloadBlankPaper(7L, 9L));

        assertEquals(400, error.getCode());
        verifyNoInteractions(dispatcher, converter);
    }

    private ExamPaper paper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(7L);
        paper.setTitle("发布试卷");
        paper.setPublished(true);
        paper.setStatus(1);
        paper.setPageSize(PageSize.A4);
        paper.setOrientation(Orientation.PORTRAIT);
        paper.setColumnsCount(1);
        paper.setRenderMode(PaperRenderMode.SIMPLE);
        paper.setSelectionMode(SelectionMode.MANUAL);
        paper.setQuestionCount(1);
        paper.setTotalScore(BigDecimal.TEN);
        return paper;
    }

    private ExamPaperQuestion question() {
        ExamPaperQuestion question = new ExamPaperQuestion();
        question.setId(101L);
        question.setPaperId(7L);
        question.setQuestionId(501L);
        question.setSortOrder(1);
        question.setSectionOrder(1);
        question.setScore(BigDecimal.TEN);
        question.setType("short_answer");
        question.setStem("快照题干");
        question.setBodyJson("{}");
        question.setAnswerJson("标准答案");
        question.setAnalysis("解析");
        question.setScoringJson("{}");
        return question;
    }
}
