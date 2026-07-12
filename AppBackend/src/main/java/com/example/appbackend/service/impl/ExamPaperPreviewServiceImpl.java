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
import jakarta.annotation.PostConstruct;
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

        PaperVO paper = transientPaper(request, layout, byId);
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
        String configHash = hash(configurationCanonical(request, layout));
        String questionHash = hash(questionCanonical(paper.getQuestions()));
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

    private String configurationCanonical(CreateRequest request, PaperLayoutConfig layout) {
        StringBuilder out = new StringBuilder("preview-config-v1");
        append(out, request.getTitle()); append(out, request.getSubtitle());
        append(out, request.getDurationMinutes()); append(out, request.getPrecautions());
        append(out, layout.getRenderMode()); append(out, layout.getPageSize()); append(out, layout.getOrientation());
        append(out, layout.getMarginPreset()); append(out, layout.getCustomMarginTop());
        append(out, layout.getCustomMarginRight()); append(out, layout.getCustomMarginBottom());
        append(out, layout.getCustomMarginLeft()); append(out, layout.getColumnsCount());
        append(out, layout.getColumnSpace()); append(out, layout.getHasBindingLine()); append(out, layout.getHeaderInfo());
        append(out, layout.getTitleFontSize()); append(out, layout.getSubtitleFontSize()); append(out, layout.getBodyFontSize());
        return out.toString();
    }

    private String questionCanonical(List<QuestionSnapshotVO> questions) {
        StringBuilder out = new StringBuilder("preview-questions-v1");
        for (QuestionSnapshotVO q : questions) {
            append(out, q.getQuestionId()); append(out, q.getSortOrder()); append(out, q.getSectionOrder());
            append(out, q.getScore() == null ? null : q.getScore().stripTrailingZeros().toPlainString());
            append(out, q.getType()); append(out, q.getStem()); append(out, q.getBodyJson());
            append(out, q.getAnswerJson()); append(out, q.getAnalysis()); append(out, q.getScoringJson());
        }
        return out.toString();
    }

    private void append(StringBuilder out, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        out.append('|').append(text.length()).append(':').append(text);
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
