package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutRequest;
import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.dto.ExamPaperDTO.RandomPreviewRequest;
import com.example.appbackend.dto.ExamPaperDTO.RandomRule;
import com.example.appbackend.dto.ExamPaperDTO.SelectedQuestion;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.ExamPaperService;
import com.example.appbackend.service.ExamPaperDocumentGenerator;
import com.example.appbackend.service.exampaper.ExamPaperDocumentDispatcher;
import com.example.appbackend.service.exampaper.ExamPaperFingerprint;
import com.example.appbackend.service.ExamPaperPreviewService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

@Service
public class ExamPaperServiceImpl implements ExamPaperService {

    private final ExamQuestionRepository questionRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final RandomGenerator randomGenerator;
    private final ExamPaperDocumentGenerator documentGenerator;
    private final ExamPaperDocumentDispatcher documentDispatcher;
    private final ExamPaperPreviewService previewService;

    public ExamPaperServiceImpl(ExamQuestionRepository questionRepository,
                                ExamPaperRepository paperRepository,
                                ExamPaperQuestionRepository paperQuestionRepository) {
        this(questionRepository, paperRepository, paperQuestionRepository,
                new Random(), new ExamPaperDocumentDispatcher());
    }

    @Autowired
    public ExamPaperServiceImpl(ExamQuestionRepository questionRepository,
                                ExamPaperRepository paperRepository,
                                ExamPaperQuestionRepository paperQuestionRepository,
                                ExamPaperPreviewService previewService) {
        this(questionRepository, paperRepository, paperQuestionRepository,
                new Random(), new ExamPaperDocumentDispatcher(), previewService);
    }

    ExamPaperServiceImpl(ExamQuestionRepository questionRepository,
                                 ExamPaperRepository paperRepository,
                                 ExamPaperQuestionRepository paperQuestionRepository,
                                 RandomGenerator randomGenerator,
                                 ExamPaperDocumentDispatcher documentDispatcher) {
        this(questionRepository, paperRepository, paperQuestionRepository, randomGenerator, documentDispatcher, null);
    }

    ExamPaperServiceImpl(ExamQuestionRepository questionRepository,
                         ExamPaperRepository paperRepository,
                         ExamPaperQuestionRepository paperQuestionRepository,
                         RandomGenerator randomGenerator,
                         ExamPaperDocumentDispatcher documentDispatcher,
                         ExamPaperPreviewService previewService) {
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.randomGenerator = randomGenerator;
        this.documentGenerator = null;
        this.documentDispatcher = documentDispatcher;
        this.previewService = previewService;
    }

    ExamPaperServiceImpl(ExamQuestionRepository questionRepository,
                         ExamPaperRepository paperRepository,
                         ExamPaperQuestionRepository paperQuestionRepository,
                         RandomGenerator randomGenerator,
                         ExamPaperDocumentGenerator documentGenerator) {
        this(questionRepository, paperRepository, paperQuestionRepository, randomGenerator, documentGenerator, null);
    }

    ExamPaperServiceImpl(ExamQuestionRepository questionRepository,
                         ExamPaperRepository paperRepository,
                         ExamPaperQuestionRepository paperQuestionRepository,
                         RandomGenerator randomGenerator,
                         ExamPaperDocumentGenerator documentGenerator,
                         ExamPaperPreviewService previewService) {
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.randomGenerator = randomGenerator;
        this.documentGenerator = documentGenerator;
        this.documentDispatcher = null;
        this.previewService = previewService;
    }

    @Override
    public PaperVO randomPreview(RandomPreviewRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        List<List<ExamQuestion>> candidatesByRule = new ArrayList<>();
        List<RuleSlot> slots = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < request.getRules().size(); ruleIndex++) {
            RandomRule rule = request.getRules().get(ruleIndex);
            List<ExamQuestion> candidates = new ArrayList<>(
                    questionRepository.findVisibleActiveCandidates(
                            rule.getType(), rule.getDifficulty(), userId));
            shuffle(candidates);
            candidatesByRule.add(candidates);
            for (int slotIndex = 0; slotIndex < rule.getQuantity(); slotIndex++) {
                slots.add(new RuleSlot(ruleIndex, slotIndex));
            }
        }

        slots.sort(Comparator
                .comparingInt((RuleSlot slot) -> candidatesByRule.get(slot.ruleIndex()).size())
                .thenComparingInt(RuleSlot::ruleIndex)
                .thenComparingInt(RuleSlot::slotIndex));
        Map<Long, RuleSlot> questionAssignments = new HashMap<>();
        Map<RuleSlot, ExamQuestion> slotAssignments = new HashMap<>();
        for (RuleSlot slot : slots) {
            assign(slot, candidatesByRule, questionAssignments, slotAssignments, new HashSet<>());
        }

        List<QuestionSnapshotVO> selected = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < request.getRules().size(); ruleIndex++) {
            int quantity = request.getRules().get(ruleIndex).getQuantity();
            for (int slotIndex = 0; slotIndex < quantity; slotIndex++) {
                ExamQuestion question = slotAssignments.get(new RuleSlot(ruleIndex, slotIndex));
                if (question == null) continue;
                QuestionSnapshotVO candidate = candidateVO(question);
                candidate.setSortOrder(selected.size() + 1);
                selected.add(candidate);
            }
        }
        PaperVO result = new PaperVO();
        result.setQuestions(selected);
        result.setQuestionCount(selected.size());
        result.setTotalScore(selected.stream()
                .map(QuestionSnapshotVO::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return result;
    }

    private boolean assign(RuleSlot slot,
                           List<List<ExamQuestion>> candidatesByRule,
                           Map<Long, RuleSlot> questionAssignments,
                           Map<RuleSlot, ExamQuestion> slotAssignments,
                           Set<Long> visitedQuestionIds) {
        for (ExamQuestion candidate : candidatesByRule.get(slot.ruleIndex())) {
            if (!visitedQuestionIds.add(candidate.getId())) {
                continue;
            }
            RuleSlot occupiedSlot = questionAssignments.get(candidate.getId());
            if (occupiedSlot == null
                    || assign(occupiedSlot, candidatesByRule, questionAssignments,
                    slotAssignments, visitedQuestionIds)) {
                questionAssignments.put(candidate.getId(), slot);
                slotAssignments.put(slot, candidate);
                return true;
            }
        }
        return false;
    }

    private record RuleSlot(int ruleIndex, int slotIndex) {
    }

    @Override
    @Transactional
    public PaperVO create(CreateRequest request, Long userId) {
        PaperLayoutRequest layout = normalizeLayout(request);
        validateLayout(layout);
        validateUniqueSelections(request.getQuestions());
        List<Long> questionIds = request.getQuestions().stream()
                .map(SelectedQuestion::getQuestionId)
                .toList();
        List<ExamQuestion> loaded = questionRepository.findAllVisibleById(questionIds, userId);
        if (loaded.size() != questionIds.size()
                || loaded.stream().anyMatch(question -> !Integer.valueOf(1).equals(question.getStatus()))) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题目不存在或已停用");
        }
        Map<Long, ExamQuestion> questionsById = new HashMap<>();
        loaded.forEach(question -> questionsById.put(question.getId(), question));
        if (questionsById.size() != questionIds.size()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题目不存在或已停用");
        }

        List<SelectedQuestion> selections = request.getQuestions().stream()
                .sorted(Comparator.comparing(SelectedQuestion::getSortOrder))
                .toList();
        var fingerprintQuestions = ExamPaperFingerprint.snapshot(selections, questionsById);
        PaperLayoutConfig layoutConfig = ExamPaperFingerprint.layout(layout);
        if (layout.getRenderMode() == com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode.TEMPLATE) {
            if (request.getPreviewProof() == null)
                throw new BusinessException(409, "模板试卷必须先生成有效预览");
            if (previewService == null)
                throw new BusinessException(409, "预览证明校验服务不可用");
            previewService.validateAndConsumeProof(request.getPreviewProof(), userId,
                    ExamPaperFingerprint.compute(request, layoutConfig, fingerprintQuestions));
        }
        BigDecimal totalScore = selections.stream()
                .map(SelectedQuestion::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ExamPaper paper = new ExamPaper();
        paper.setTitle(request.getTitle());
        paper.setSubtitle(request.getSubtitle());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper.setPrecautions(request.getPrecautions());
        copyLayout(layout, paper);
        paper.setSelectionMode(request.getSelectionMode());
        paper.setQuestionCount(selections.size());
        paper.setTotalScore(totalScore);
        paper.setCreatedBy(userId);
        paper = paperRepository.save(paper);

        List<ExamPaperQuestion> snapshots = new ArrayList<>();
        Map<String, Integer> sectionOrders = new LinkedHashMap<>();
        for (SelectedQuestion selection : selections) {
            ExamQuestion question = questionsById.get(selection.getQuestionId());
            int sectionOrder = sectionOrders.computeIfAbsent(question.getType(), ignored -> sectionOrders.size() + 1);
            snapshots.add(snapshot(paper.getId(), selection, question, sectionOrder));
        }
        paperQuestionRepository.saveAll(snapshots);
        return toVO(paper, snapshots);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaperVO> list(Integer current, Integer size, String keyword, Long userId) {
        int pageNumber = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(pageNumber - 1, pageSize);
        String titleKeyword = keyword == null ? "" : keyword.trim();
        Page<ExamPaper> page = titleKeyword.isEmpty()
                ? paperRepository.findByCreatedByAndStatusOrderByCreateTimeDesc(userId, 1, pageable)
                : paperRepository.findByCreatedByAndStatusAndTitleContainingOrderByCreateTimeDesc(
                        userId, 1, titleKeyword, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(paper -> toVO(paper, null)).toList(),
                page.getTotalElements(),
                pageNumber,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PaperVO detail(Long id, Long userId) {
        ExamPaper paper = paperRepository.findById(id)
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在"));
        if (!Objects.equals(paper.getCreatedBy(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权访问该试卷");
        }
        return toVO(paper, paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(id));
    }

    @Override
    @Transactional
    public PaperVO publish(Long id, Long adminUserId) {
        ExamPaper paper = ownedActivePaper(id, adminUserId);
        if (Boolean.TRUE.equals(paper.getPublished())) {
            return toVO(paper, null);
        }
        if (paper.getQuestionCount() == null || paper.getQuestionCount() <= 0) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "空试卷不能发布");
        }
        if (paper.getDurationMinutes() == null || paper.getDurationMinutes() <= 0) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "考试时长必须为正数");
        }
        paper.setPublished(true);
        paper.setPublishTime(LocalDateTime.now());
        return toVO(paperRepository.save(paper), null);
    }

    @Override
    @Transactional
    public PaperVO unpublish(Long id, Long adminUserId) {
        ExamPaper paper = ownedActivePaper(id, adminUserId);
        if (!Boolean.TRUE.equals(paper.getPublished())) {
            return toVO(paper, null);
        }
        paper.setPublished(false);
        paper.setPublishTime(null);
        return toVO(paperRepository.save(paper), null);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadFile download(Long id, Long userId, DownloadContent content) {
        PaperVO paper = detail(id, userId);
        byte[] bytes;
        if (documentDispatcher == null) {
            // Compatibility seam for existing service unit tests; production always uses the dispatcher.
            bytes = documentGenerator.generate(paper, content);
        } else {
            bytes = documentDispatcher.generate(paper, content, paper.getLayout());
        }
        return new DownloadFile(paper.getTitle(), bytes);
    }

    private ExamPaper ownedActivePaper(Long id, Long userId) {
        ExamPaper paper = paperRepository.findByIdAndStatus(id, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在"));
        if (!Objects.equals(paper.getCreatedBy(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权访问该试卷");
        }
        return paper;
    }

    private void validateLayout(PaperLayoutRequest layout) {
        if (layout == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "页面格式不能为空");
        }
        if (layout.getMarginPreset() == MarginPreset.CUSTOM
                && (layout.getCustomMarginTop() == null || layout.getCustomMarginRight() == null
                || layout.getCustomMarginBottom() == null || layout.getCustomMarginLeft() == null)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "自定义页边距必须完整填写");
        }
        if (layout.getRenderMode() == null || layout.getPageSize() == null
                || layout.getOrientation() == null || layout.getMarginPreset() == null
                || layout.getColumnsCount() == null || layout.getColumnSpace() == null
                || layout.getHasBindingLine() == null || layout.getTitleFontSize() == null
                || layout.getSubtitleFontSize() == null || layout.getBodyFontSize() == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "页面格式参数不完整");
        }
    }

    private PaperLayoutRequest normalizeLayout(CreateRequest request) {
        if (request.getLayout() != null) {
            return request.getLayout();
        }
        if (request.getPageSize() == null || request.getOrientation() == null
                || request.getColumnsCount() == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "页面格式参数不完整");
        }
        PaperLayoutRequest legacy = new PaperLayoutRequest();
        legacy.setRenderMode(com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode.SIMPLE);
        legacy.setPageSize(request.getPageSize());
        legacy.setOrientation(request.getOrientation());
        legacy.setMarginPreset(MarginPreset.NORMAL);
        legacy.setColumnsCount(request.getColumnsCount());
        legacy.setColumnSpace(425);
        legacy.setHasBindingLine(false);
        legacy.setHeaderInfo(request.getHeaderInfo());
        legacy.setTitleFontSize(50);
        legacy.setSubtitleFontSize(24);
        legacy.setBodyFontSize(21);
        return legacy;
    }

    private void copyLayout(PaperLayoutRequest layout, ExamPaper paper) {
        paper.setRenderMode(layout.getRenderMode());
        paper.setPageSize(layout.getPageSize());
        paper.setOrientation(layout.getOrientation());
        paper.setMarginPreset(layout.getMarginPreset());
        paper.setCustomMarginTop(layout.getCustomMarginTop());
        paper.setCustomMarginRight(layout.getCustomMarginRight());
        paper.setCustomMarginBottom(layout.getCustomMarginBottom());
        paper.setCustomMarginLeft(layout.getCustomMarginLeft());
        paper.setColumnsCount(layout.getColumnsCount());
        paper.setColumnSpace(layout.getColumnSpace());
        paper.setHasBindingLine(layout.getHasBindingLine());
        paper.setHeaderInfo(layout.getHeaderInfo());
        paper.setTitleFontSize(layout.getTitleFontSize());
        paper.setSubtitleFontSize(layout.getSubtitleFontSize());
        paper.setBodyFontSize(layout.getBodyFontSize());
    }

    private void validateUniqueSelections(List<SelectedQuestion> selections) {
        Set<Long> questionIds = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (SelectedQuestion selection : selections) {
            if (!questionIds.add(selection.getQuestionId())) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "题目不能重复");
            }
            if (!sortOrders.add(selection.getSortOrder())) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "题目排序不能重复");
            }
        }
    }

    private ExamPaperQuestion snapshot(Long paperId, SelectedQuestion selection, ExamQuestion question,
                                       int sectionOrder) {
        ExamPaperQuestion snapshot = new ExamPaperQuestion();
        snapshot.setPaperId(paperId);
        snapshot.setQuestionId(question.getId());
        snapshot.setSortOrder(selection.getSortOrder());
        snapshot.setSectionOrder(sectionOrder);
        snapshot.setScore(selection.getScore());
        snapshot.setType(question.getType());
        snapshot.setStem(question.getStem());
        snapshot.setBodyJson(question.getBodyJson());
        snapshot.setAnswerJson(question.getAnswerJson());
        snapshot.setAnalysis(question.getAnalysis());
        snapshot.setScoringJson(question.getScoringJson());
        return snapshot;
    }

    private PaperVO toVO(ExamPaper paper, List<ExamPaperQuestion> snapshots) {
        PaperVO vo = new PaperVO();
        vo.setId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setSubtitle(paper.getSubtitle());
        vo.setDurationMinutes(paper.getDurationMinutes());
        vo.setPrecautions(paper.getPrecautions());
        vo.setHeaderInfo(paper.getHeaderInfo());
        vo.setPageSize(paper.getPageSize());
        vo.setOrientation(paper.getOrientation());
        vo.setColumnsCount(paper.getColumnsCount());
        vo.setLayout(toLayout(paper));
        vo.setSelectionMode(paper.getSelectionMode());
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPublished(Boolean.TRUE.equals(paper.getPublished()));
        vo.setPublishTime(paper.getPublishTime());
        vo.setCreateTime(paper.getCreateTime());
        if (snapshots != null) {
            vo.setQuestions(snapshots.stream().map(this::snapshotVO).toList());
        }
        return vo;
    }

    private PaperLayoutConfig toLayout(ExamPaper paper) {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        layout.setRenderMode(Objects.requireNonNullElse(paper.getRenderMode(),
                com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode.SIMPLE));
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

    private QuestionSnapshotVO snapshotVO(ExamPaperQuestion snapshot) {
        QuestionSnapshotVO vo = new QuestionSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setQuestionId(snapshot.getQuestionId());
        vo.setSortOrder(snapshot.getSortOrder());
        vo.setSectionOrder(snapshot.getSectionOrder());
        vo.setScore(snapshot.getScore());
        vo.setType(snapshot.getType());
        vo.setStem(snapshot.getStem());
        vo.setBodyJson(snapshot.getBodyJson());
        vo.setAnswerJson(snapshot.getAnswerJson());
        vo.setAnalysis(snapshot.getAnalysis());
        vo.setScoringJson(snapshot.getScoringJson());
        return vo;
    }

    private void shuffle(List<ExamQuestion> candidates) {
        for (int i = candidates.size() - 1; i > 0; i--) {
            int selected = randomGenerator.nextInt(i + 1);
            ExamQuestion value = candidates.get(i);
            candidates.set(i, candidates.get(selected));
            candidates.set(selected, value);
        }
    }

    private QuestionSnapshotVO candidateVO(ExamQuestion question) {
        QuestionSnapshotVO vo = new QuestionSnapshotVO();
        vo.setQuestionId(question.getId());
        vo.setScore(question.getScore());
        vo.setType(question.getType());
        vo.setStem(question.getStem());
        vo.setBodyJson(question.getBodyJson());
        vo.setAnswerJson(question.getAnswerJson());
        vo.setAnalysis(question.getAnalysis());
        vo.setScoringJson(question.getScoringJson());
        return vo;
    }
}
