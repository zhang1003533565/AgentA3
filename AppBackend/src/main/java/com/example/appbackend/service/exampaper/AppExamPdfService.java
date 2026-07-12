package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AppExamPdfService {
    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository questionRepository;
    private final ExamPaperDocumentDispatcher dispatcher;
    private final LibreOfficePreviewConverter converter;
    private final Path workRoot;

    @Autowired
    public AppExamPdfService(ExamPaperRepository paperRepository,
            ExamPaperQuestionRepository questionRepository,
            @Value("${exam-paper.preview.soffice-path:soffice}") String sofficePath,
            @Value("${exam-paper.preview.timeout-seconds:30}") long timeoutSeconds,
            @Value("${exam-paper.app-pdf.root:${java.io.tmpdir}/agent-a3-app-exam-pdf}") String root) {
        this(paperRepository, questionRepository, new ExamPaperDocumentDispatcher(),
                new LibreOfficePreviewConverter(sofficePath, Duration.ofSeconds(timeoutSeconds), Path.of(root)),
                Path.of(root));
    }

    AppExamPdfService(ExamPaperRepository paperRepository,
            ExamPaperQuestionRepository questionRepository,
            ExamPaperDocumentDispatcher dispatcher,
            LibreOfficePreviewConverter converter,
            Path workRoot) {
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.dispatcher = dispatcher;
        this.converter = converter;
        this.workRoot = workRoot.toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public PdfFile downloadBlankPaper(Long paperId, Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        ExamPaper paper = paperRepository.findByIdAndStatusAndPublishedTrue(paperId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在或已下架"));
        List<ExamPaperQuestion> questions = questionRepository.findByPaperIdOrderBySortOrderAscIdAsc(paperId);
        if (questions.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "试卷没有题目");
        }
        PaperVO view = toView(paper, questions);
        byte[] docx = dispatcher.generate(view, DownloadContent.PAPER, view.getLayout());
        Path directory = workRoot.resolve(Long.toString(userId)).resolve(UUID.randomUUID().toString());
        try {
            LibreOfficePreviewConverter.ConversionResult converted = converter.convert(docx, directory);
            return new PdfFile(paper.getTitle(), converted.bytes());
        } finally {
            converter.deleteRecursively(directory);
        }
    }

    private PaperVO toView(ExamPaper paper, List<ExamPaperQuestion> questions) {
        PaperVO view = new PaperVO();
        view.setId(paper.getId());
        view.setTitle(paper.getTitle());
        view.setSubtitle(paper.getSubtitle());
        view.setDurationMinutes(paper.getDurationMinutes());
        view.setPrecautions(paper.getPrecautions());
        view.setHeaderInfo(paper.getHeaderInfo());
        view.setPageSize(paper.getPageSize());
        view.setOrientation(paper.getOrientation());
        view.setColumnsCount(paper.getColumnsCount());
        view.setSelectionMode(paper.getSelectionMode());
        view.setQuestionCount(paper.getQuestionCount());
        view.setTotalScore(paper.getTotalScore());
        view.setLayout(toLayout(paper));
        view.setQuestions(questions.stream().map(this::toQuestion).toList());
        return view;
    }

    private PaperLayoutConfig toLayout(ExamPaper paper) {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        layout.setRenderMode(Objects.requireNonNullElse(paper.getRenderMode(), PaperRenderMode.SIMPLE));
        if (paper.getPageSize() != null) layout.setPageSize(paper.getPageSize());
        if (paper.getOrientation() != null) layout.setOrientation(paper.getOrientation());
        layout.setMarginPreset(Objects.requireNonNullElse(paper.getMarginPreset(), MarginPreset.NORMAL));
        layout.setCustomMarginTop(paper.getCustomMarginTop());
        layout.setCustomMarginRight(paper.getCustomMarginRight());
        layout.setCustomMarginBottom(paper.getCustomMarginBottom());
        layout.setCustomMarginLeft(paper.getCustomMarginLeft());
        if (paper.getColumnsCount() != null) layout.setColumnsCount(paper.getColumnsCount());
        layout.setColumnSpace(Objects.requireNonNullElse(paper.getColumnSpace(), 425));
        layout.setHasBindingLine(Objects.requireNonNullElse(paper.getHasBindingLine(), false));
        layout.setHeaderInfo(paper.getHeaderInfo());
        layout.setTitleFontSize(Objects.requireNonNullElse(paper.getTitleFontSize(), 50));
        layout.setSubtitleFontSize(Objects.requireNonNullElse(paper.getSubtitleFontSize(), 24));
        layout.setBodyFontSize(Objects.requireNonNullElse(paper.getBodyFontSize(), 21));
        return layout;
    }

    private QuestionSnapshotVO toQuestion(ExamPaperQuestion question) {
        QuestionSnapshotVO view = new QuestionSnapshotVO();
        view.setId(question.getId());
        view.setQuestionId(question.getQuestionId());
        view.setSortOrder(question.getSortOrder());
        view.setSectionOrder(question.getSectionOrder());
        view.setScore(question.getScore());
        view.setType(question.getType());
        view.setStem(question.getStem());
        view.setBodyJson(question.getBodyJson());
        view.setAnswerJson(question.getAnswerJson());
        view.setAnalysis(question.getAnalysis());
        view.setScoringJson(question.getScoringJson());
        return view;
    }

    public record PdfFile(String title, byte[] bytes) { }
}
