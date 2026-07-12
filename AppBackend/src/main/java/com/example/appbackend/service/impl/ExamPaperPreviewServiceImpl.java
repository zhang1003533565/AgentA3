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
import com.example.appbackend.service.exampaper.ExamPaperFingerprint;
import com.example.appbackend.service.exampaper.ExamPaperFingerprint.FingerprintQuestion;
import com.example.appbackend.service.exampaper.ExamPaperFingerprint.Fingerprints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Duration cleanupInterval;
    private final Clock clock;
    private final Map<String, Metadata> previews = new ConcurrentHashMap<>();

    @Autowired
    public ExamPaperPreviewServiceImpl(ExamQuestionRepository questionRepository,
            @Value("${exam-paper.preview.soffice-path:soffice}") String sofficePath,
            @Value("${exam-paper.preview.timeout-seconds:30}") long timeoutSeconds,
            @Value("${exam-paper.preview.root:${java.io.tmpdir}/agent-a3-exam-preview}") String root,
            @Value("${exam-paper.preview.ttl-minutes:30}") long ttlMinutes,
            @Value("${exam-paper.preview.cleanup-interval-ms:60000}") long cleanupIntervalMs) {
        this(questionRepository, new ExamPaperDocumentDispatcher(), Path.of(root),
                Duration.ofMinutes(ttlMinutes), Clock.systemUTC(), sofficePath, Duration.ofSeconds(timeoutSeconds),
                Duration.ofMillis(cleanupIntervalMs));
    }

    ExamPaperPreviewServiceImpl(ExamQuestionRepository questionRepository,
            ExamPaperDocumentDispatcher dispatcher, Path root, Duration ttl, Clock clock,
            String sofficePath, Duration timeout) {
        this(questionRepository, dispatcher, root, ttl, clock, sofficePath, timeout, Duration.ofMinutes(1));
    }

    ExamPaperPreviewServiceImpl(ExamQuestionRepository questionRepository,
            ExamPaperDocumentDispatcher dispatcher, Path root, Duration ttl, Clock clock,
            String sofficePath, Duration timeout, Duration cleanupInterval) {
        this.questionRepository = questionRepository;
        this.dispatcher = dispatcher;
        this.root = root.toAbsolutePath().normalize();
        validateLifecycle(ttl);
        this.ttl = ttl;
        validateCleanupInterval(cleanupInterval);
        this.cleanupInterval = cleanupInterval;
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
        validateLifecycle(ttl);
        this.ttl = ttl;
        this.cleanupInterval = Duration.ofMinutes(1);
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

        List<FingerprintQuestion> snapshot = ExamPaperFingerprint.snapshot(request.getQuestions(), byId);
        PaperVO paper = transientPaper(request, layout, snapshot);
        byte[] docx = dispatcher.generate(paper, DownloadContent.PAPER, layout);
        String token = UUID.randomUUID().toString();
        Path directory = root.resolve(Long.toString(userId)).resolve(token);
        LibreOfficePreviewConverter.ConversionResult converted;
        try {
            converted = converter.convert(docx, directory);
        } catch (RuntimeException exception) {
            try { converter.deleteRecursively(directory); } catch (RuntimeException ignored) { }
            throw exception;
        }
        Path storedPdf = directory.resolve(token + ".pdf");
        try {
            Files.createDirectories(directory);
            Files.write(storedPdf, converted.bytes());
        } catch (Exception exception) {
            converter.deleteRecursively(directory);
            throw new BusinessException(Result.ERROR_CODE, "无法保存试卷预览");
        }
        Instant expiresAt = clock.instant().plus(ttl);
        Fingerprints fingerprints = ExamPaperFingerprint.compute(request, layout, snapshot);
        String configHash = fingerprints.configurationHash();
        String questionHash = fingerprints.questionHash();
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

    @Override
    public void validateAndConsumeProof(PreviewProof proof, Long userId, Fingerprints fingerprints) {
        if (proof == null) throw new BusinessException(409, "模板试卷必须先生成有效预览");
        Metadata metadata = previews.get(proof.getToken());
        if (metadata == null || !metadata.expiresAt.isAfter(clock.instant())) {
            if (metadata != null && previews.remove(proof.getToken(), metadata))
                converter.deleteRecursively(metadata.path.getParent());
            throw new BusinessException(409, "试卷预览证明不存在或已过期，请重新预览");
        }
        if (!Objects.equals(metadata.userId, userId)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权使用该试卷预览证明");
        boolean matches = Objects.equals(metadata.configurationHash, proof.getConfigurationHash())
                && Objects.equals(metadata.questionHash, proof.getQuestionHash())
                && Objects.equals(metadata.configurationHash, fingerprints.configurationHash())
                && Objects.equals(metadata.questionHash, fingerprints.questionHash());
        if (!matches) throw new BusinessException(409, "试卷内容或页面格式已变化，请重新预览");
        if (!previews.remove(proof.getToken(), metadata))
            throw new BusinessException(409, "试卷预览证明已被使用，请重新预览");
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
        cleanupOrphans(now);
    }

    @PostConstruct
    public void initialize() {
        converter.initializeRoot();
        cleanupExpired();
    }

    private void cleanupOrphans(Instant now) {
        converter.requireOwnershipMarker();
        if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) return;
        try (var users = Files.list(root)) {
            users.filter(path -> Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(path) && path.getFileName().toString().matches("[1-9][0-9]*"))
                    .forEach(user -> {
                        try (var tokens = Files.list(user)) {
                            tokens.filter(path -> Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                                            && !Files.isSymbolicLink(path) && isUuid(path.getFileName().toString()))
                                    .forEach(path -> {
                                        String token = path.getFileName().toString();
                                        try {
                                            Instant modified = Files.getLastModifiedTime(path,
                                                    java.nio.file.LinkOption.NOFOLLOW_LINKS).toInstant();
                                            if (!previews.containsKey(token) && !modified.plus(ttl).isAfter(now))
                                                converter.deleteRecursively(path);
                                        } catch (Exception ignored) { }
                                    });
                        } catch (Exception ignored) { }
                    });
        } catch (Exception ignored) { }
    }

    private boolean isUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException exception) { return false; }
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
        return ExamPaperFingerprint.layout(source);
    }

    private void validateSelections(List<SelectedQuestion> selections) {
        if (selections == null || selections.isEmpty()) throw new BusinessException(Result.BAD_REQUEST_CODE, "请选择题目");
        Set<Long> ids = new HashSet<>(); Set<Integer> orders = new HashSet<>();
        for (SelectedQuestion selection : selections) {
            if (!ids.add(selection.getQuestionId())) throw new BusinessException(Result.BAD_REQUEST_CODE, "题目不能重复");
            if (!orders.add(selection.getSortOrder())) throw new BusinessException(Result.BAD_REQUEST_CODE, "题目排序不能重复");
        }
    }

    private PaperVO transientPaper(CreateRequest request, PaperLayoutConfig layout, List<FingerprintQuestion> questions) {
        PaperVO paper = new PaperVO(); paper.setTitle(request.getTitle()); paper.setSubtitle(request.getSubtitle());
        paper.setDurationMinutes(request.getDurationMinutes()); paper.setPrecautions(request.getPrecautions());
        paper.setLayout(layout); paper.setHeaderInfo(layout.getHeaderInfo()); paper.setPageSize(layout.getPageSize());
        paper.setOrientation(layout.getOrientation()); paper.setColumnsCount(layout.getColumnsCount());
        paper.setSelectionMode(request.getSelectionMode());
        List<QuestionSnapshotVO> snapshots = questions.stream().map(q -> {
            QuestionSnapshotVO vo = new QuestionSnapshotVO(); vo.setQuestionId(q.questionId()); vo.setSortOrder(q.sortOrder());
            vo.setSectionOrder(q.sectionOrder()); vo.setScore(q.score()); vo.setType(q.type()); vo.setStem(q.stem());
            vo.setBodyJson(q.bodyJson()); vo.setAnswerJson(q.answerJson()); vo.setAnalysis(q.analysis());
            vo.setScoringJson(q.scoringJson()); return vo;
        }).toList();
        paper.setQuestions(snapshots); paper.setQuestionCount(snapshots.size());
        paper.setTotalScore(snapshots.stream().map(QuestionSnapshotVO::getScore).reduce(BigDecimal.ZERO, BigDecimal::add));
        return paper;
    }

    private void validateLifecycle(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("预览 TTL 必须在 0 到 24 小时之间");
    }

    private void validateCleanupInterval(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()
                || interval.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("预览清理间隔必须在 0 到 1 小时之间");
    }

    private record Metadata(Long userId, Path path, Instant expiresAt, String configurationHash,
                            String questionHash, int pageCount, String title) {}
}
