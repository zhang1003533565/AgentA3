package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamPaperDTO.*;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewFile;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewResponse;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.ExamPaperPreviewService;
import com.example.appbackend.service.exampaper.ExamPaperDocumentDispatcher;
import com.example.appbackend.service.exampaper.LibreOfficePreviewConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExamPaperPreviewServiceImpl implements ExamPaperPreviewService {
    private final ExamQuestionRepository questionRepository;
    private final ExamPaperDocumentDispatcher dispatcher;
    private final LibreOfficePreviewConverter converter;
    private final Path root;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Metadata> previews = new ConcurrentHashMap<>();

    @Autowired
    public ExamPaperPreviewServiceImpl(ExamQuestionRepository questionRepository,
            @Value("${exam-paper.preview.soffice-path:soffice}") String sofficePath,
            @Value("${exam-paper.preview.timeout-seconds:30}") long timeoutSeconds,
            @Value("${exam-paper.preview.root:${java.io.tmpdir}/agent-a3-exam-preview}") String root,
            @Value("${exam-paper.preview.ttl-minutes:30}") long ttlMinutes) {
        this(questionRepository, new ExamPaperDocumentDispatcher(), Path.of(root),
                Duration.ofMinutes(ttlMinutes), Clock.systemUTC(), sofficePath, Duration.ofSeconds(timeoutSeconds));
    }

    ExamPaperPreviewServiceImpl(ExamQuestionRepository questionRepository,
            ExamPaperDocumentDispatcher dispatcher, Path root, Duration ttl, Clock clock,
            String sofficePath, Duration timeout) {
        this.questionRepository = questionRepository;
        this.dispatcher = dispatcher;
        this.root = root.toAbsolutePath().normalize();
        this.ttl = ttl;
        this.clock = clock;
        this.converter = new LibreOfficePreviewConverter(sofficePath, timeout, this.root);
    }

    ExamPaperPreviewServiceImpl(ExamQuestionRepository questionRepository,
            ExamPaperDocumentDispatcher dispatcher, LibreOfficePreviewConverter converter,
            Path root, Duration ttl, Clock clock) {
        this.questionRepository = questionRepository;
        this.dispatcher = dispatcher;
        this.converter = converter;
        this.root = root.toAbsolutePath().normalize();
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public PreviewResponse createPreview(CreateRequest request, Long userId) {
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        PaperLayoutConfig layout = layout(request);
        validateSelections(request.getQuestions());
        List<Long> ids = request.getQuestions().stream().map(SelectedQuestion::getQuestionId).toList();
        List<ExamQuestion> loaded = questionRepository.findAllById(ids);
        Map<Long, ExamQuestion> byId = new HashMap<>();
        loaded.stream().filter(q -> Integer.valueOf(1).equals(q.getStatus())).forEach(q -> byId.put(q.getId(), q));
        if (byId.size() != ids.size()) throw new BusinessException(Result.BAD_REQUEST_CODE, "题目不存在或已停用");

        PaperVO paper = transientPaper(request, layout, byId);
        byte[] docx = dispatcher.generate(paper, DownloadContent.PAPER, layout);
        String token = UUID.randomUUID().toString();
        Path directory = root.resolve(Long.toString(userId)).resolve(token);
        LibreOfficePreviewConverter.ConversionResult converted = converter.convert(docx, directory);
        Path storedPdf = directory.resolve(token + ".pdf");
        try {
            Files.createDirectories(directory);
            Files.write(storedPdf, converted.bytes());
        } catch (Exception exception) {
            converter.deleteRecursively(directory);
            throw new BusinessException(Result.ERROR_CODE, "无法保存试卷预览");
        }
        Instant expiresAt = clock.instant().plus(ttl);
        String configHash = hash(layout.toString());
        String questionHash = hash(paper.getQuestions().toString());
        previews.put(token, new Metadata(userId, storedPdf, expiresAt,
                configHash, questionHash, converted.pageCount(), paper.getTitle()));
        return new PreviewResponse(token, "/api/exam/papers/preview/" + token,
                expiresAt, configHash, questionHash, converted.pageCount());
    }

    @Override
    public PreviewFile getPreview(String token, Long userId) {
        Metadata metadata = owned(token, userId);
        try {
            return new PreviewFile((metadata.title == null || metadata.title.isBlank() ? "试卷" : metadata.title) + "-预览.pdf",
                    Files.readAllBytes(metadata.path), metadata.pageCount);
        } catch (Exception exception) {
            previews.remove(token);
            converter.deleteRecursively(metadata.path.getParent());
            throw new BusinessException(Result.NOT_FOUND_CODE, "试卷预览不存在");
        }
    }

    @Override
    public void deletePreview(String token, Long userId) {
        Metadata metadata = owned(token, userId);
        previews.remove(token, metadata);
        converter.deleteRecursively(metadata.path.getParent());
    }

    @Scheduled(fixedDelayString = "${exam-paper.preview.cleanup-interval-ms:60000}")
    public void cleanupExpired() {
        Instant now = clock.instant();
        previews.forEach((token, metadata) -> {
            if (!metadata.expiresAt.isAfter(now) && previews.remove(token, metadata)) {
                converter.deleteRecursively(metadata.path.getParent());
            }
        });
    }

    private Metadata owned(String token, Long userId) {
        Metadata metadata = previews.get(token);
        if (metadata == null) throw new BusinessException(Result.NOT_FOUND_CODE, "试卷预览不存在或已过期");
        if (!metadata.expiresAt.isAfter(clock.instant())) {
            previews.remove(token, metadata);
            converter.deleteRecursively(metadata.path.getParent());
            throw new BusinessException(Result.NOT_FOUND_CODE, "试卷预览不存在或已过期");
        }
        if (!Objects.equals(metadata.userId, userId)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权访问该试卷预览");
        return metadata;
    }

    private PaperLayoutConfig layout(CreateRequest request) {
        PaperLayoutRequest source = request.getLayout();
        if (source == null) throw new BusinessException(Result.BAD_REQUEST_CODE, "页面格式不能为空");
        if (source.getRenderMode() == null || source.getPageSize() == null || source.getOrientation() == null
                || source.getMarginPreset() == null || source.getColumnsCount() == null || source.getColumnSpace() == null
                || source.getHasBindingLine() == null || source.getTitleFontSize() == null
                || source.getSubtitleFontSize() == null || source.getBodyFontSize() == null)
            throw new BusinessException(Result.BAD_REQUEST_CODE, "页面格式参数不完整");
        if (source.getMarginPreset() == MarginPreset.CUSTOM && (source.getCustomMarginTop() == null
                || source.getCustomMarginRight() == null || source.getCustomMarginBottom() == null
                || source.getCustomMarginLeft() == null))
            throw new BusinessException(Result.BAD_REQUEST_CODE, "自定义页边距必须完整填写");
        PaperLayoutConfig target = new PaperLayoutConfig();
        target.setRenderMode(source.getRenderMode()); target.setPageSize(source.getPageSize());
        target.setOrientation(source.getOrientation()); target.setMarginPreset(source.getMarginPreset());
        target.setCustomMarginTop(source.getCustomMarginTop()); target.setCustomMarginRight(source.getCustomMarginRight());
        target.setCustomMarginBottom(source.getCustomMarginBottom()); target.setCustomMarginLeft(source.getCustomMarginLeft());
        target.setColumnsCount(source.getColumnsCount()); target.setColumnSpace(source.getColumnSpace());
        target.setHasBindingLine(source.getHasBindingLine()); target.setHeaderInfo(source.getHeaderInfo());
        target.setTitleFontSize(source.getTitleFontSize()); target.setSubtitleFontSize(source.getSubtitleFontSize());
        target.setBodyFontSize(source.getBodyFontSize());
        return target;
    }

    private void validateSelections(List<SelectedQuestion> selections) {
        if (selections == null || selections.isEmpty()) throw new BusinessException(Result.BAD_REQUEST_CODE, "请选择题目");
        Set<Long> ids = new HashSet<>(); Set<Integer> orders = new HashSet<>();
        for (SelectedQuestion selection : selections) {
            if (!ids.add(selection.getQuestionId())) throw new BusinessException(Result.BAD_REQUEST_CODE, "题目不能重复");
            if (!orders.add(selection.getSortOrder())) throw new BusinessException(Result.BAD_REQUEST_CODE, "题目排序不能重复");
        }
    }

    private PaperVO transientPaper(CreateRequest request, PaperLayoutConfig layout, Map<Long, ExamQuestion> questions) {
        PaperVO paper = new PaperVO(); paper.setTitle(request.getTitle()); paper.setSubtitle(request.getSubtitle());
        paper.setDurationMinutes(request.getDurationMinutes()); paper.setPrecautions(request.getPrecautions());
        paper.setLayout(layout); paper.setHeaderInfo(layout.getHeaderInfo()); paper.setPageSize(layout.getPageSize());
        paper.setOrientation(layout.getOrientation()); paper.setColumnsCount(layout.getColumnsCount());
        paper.setSelectionMode(request.getSelectionMode());
        Map<String,Integer> sections = new LinkedHashMap<>(); List<QuestionSnapshotVO> snapshots = new ArrayList<>();
        request.getQuestions().stream().sorted(Comparator.comparing(SelectedQuestion::getSortOrder)).forEach(selection -> {
            ExamQuestion q = questions.get(selection.getQuestionId()); QuestionSnapshotVO vo = new QuestionSnapshotVO();
            vo.setQuestionId(q.getId()); vo.setSortOrder(selection.getSortOrder());
            vo.setSectionOrder(sections.computeIfAbsent(q.getType(), ignored -> sections.size() + 1));
            vo.setScore(selection.getScore()); vo.setType(q.getType()); vo.setStem(q.getStem()); vo.setBodyJson(q.getBodyJson());
            vo.setAnswerJson(q.getAnswerJson()); vo.setAnalysis(q.getAnalysis()); vo.setScoringJson(q.getScoringJson()); snapshots.add(vo);
        });
        paper.setQuestions(snapshots); paper.setQuestionCount(snapshots.size());
        paper.setTotalScore(snapshots.stream().map(QuestionSnapshotVO::getScore).reduce(BigDecimal.ZERO, BigDecimal::add));
        return paper;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private record Metadata(Long userId, Path path, Instant expiresAt, String configurationHash,
                            String questionHash, int pageCount, String title) {}
}
