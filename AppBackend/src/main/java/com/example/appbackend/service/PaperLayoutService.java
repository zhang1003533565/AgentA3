package com.example.appbackend.service;

import com.example.appbackend.entity.Paper;
import com.example.appbackend.entity.PaperLayout;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.PaperLayoutRepository;
import com.example.appbackend.repository.PaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

@Service
public class PaperLayoutService {
    private static final Set<String> PAPER_SIZES = Set.of("A4", "A3");
    private static final Set<String> ORIENTATIONS = Set.of("portrait", "landscape");
    private static final Set<String> BINDING_POSITIONS = Set.of("left", "right");

    private final PaperRepository paperRepository;
    private final PaperLayoutRepository layoutRepository;

    public PaperLayoutService(PaperRepository paperRepository, PaperLayoutRepository layoutRepository) {
        this.paperRepository = paperRepository;
        this.layoutRepository = layoutRepository;
    }

    public PaperLayout get(Long paperId, Long userId) {
        ownPaper(paperId, userId);
        return layoutRepository.findByPaperId(paperId).orElseGet(() -> defaults(paperId));
    }

    public PaperLayout getDefaults(Long paperId, Long userId) {
        ownPaper(paperId, userId);
        return defaults(paperId);
    }

    @Transactional
    public PaperLayout save(Long paperId, PaperLayout request, Long userId) {
        Paper paper = ownPaper(paperId, userId);
        validate(request);
        PaperLayout layout = layoutRepository.findByPaperId(paperId).orElseGet(PaperLayout::new);
        layout.setPaperId(paperId);
        layout.setPaper(paper);
        copy(request, layout);
        return layoutRepository.save(layout);
    }

    public PaperLayout defaults(Long paperId) {
        PaperLayout layout = new PaperLayout();
        layout.setPaperId(paperId);
        layout.setPaperSize("A4");
        layout.setOrientation("portrait");
        layout.setColumnsCount(1);
        layout.setColumnGap(new BigDecimal("0.75"));
        layout.setBindingLine(false);
        layout.setBindingPosition("left");
        layout.setMarginTop(new BigDecimal("2.54"));
        layout.setMarginBottom(new BigDecimal("2.54"));
        layout.setMarginLeft(new BigDecimal("2.54"));
        layout.setMarginRight(new BigDecimal("2.54"));
        layout.setShowSchool(true);
        layout.setShowGrade(true);
        layout.setShowClass(true);
        layout.setShowName(true);
        layout.setShowStudentNo(true);
        layout.setTitleFontSize(24);
        layout.setSubtitleFontSize(18);
        layout.setBodyFontSize(12);
        return layout;
    }

    private Paper ownPaper(Long paperId, Long userId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在"));
        if (!Objects.equals(paper.getCreatorId(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权操作该试卷版式");
        }
        return paper;
    }

    private void validate(PaperLayout layout) {
        if (!PAPER_SIZES.contains(layout.getPaperSize())) bad("纸张仅支持A4或A3");
        if (!ORIENTATIONS.contains(layout.getOrientation())) bad("纸张方向参数错误");
        if (layout.getColumnsCount() == null || (layout.getColumnsCount() != 1 && layout.getColumnsCount() != 2)) bad("栏数仅支持单栏或双栏");
        if (layout.getColumnGap() == null || layout.getColumnGap().compareTo(BigDecimal.ZERO) < 0 || layout.getColumnGap().compareTo(new BigDecimal("10")) > 0) bad("栏距必须在0到10之间");
        if (!BINDING_POSITIONS.contains(layout.getBindingPosition())) bad("装订线位置参数错误");
        validateMargin(layout.getMarginTop());
        validateMargin(layout.getMarginBottom());
        validateMargin(layout.getMarginLeft());
        validateMargin(layout.getMarginRight());
        if (layout.getBindingLine() == null || layout.getShowSchool() == null || layout.getShowGrade() == null
                || layout.getShowClass() == null || layout.getShowName() == null || layout.getShowStudentNo() == null) bad("显示配置不能为空");
        validateFont(layout.getTitleFontSize(), "标题字号");
        validateFont(layout.getSubtitleFontSize(), "副标题字号");
        validateFont(layout.getBodyFontSize(), "正文字号");
    }

    private void validateMargin(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("10")) > 0) bad("页边距必须在0到10之间");
    }

    private void validateFont(Integer value, String name) {
        if (value == null || value < 8 || value > 72) bad(name + "必须在8到72之间");
    }

    private void bad(String message) {
        throw new BusinessException(Result.BAD_REQUEST_CODE, message);
    }

    private void copy(PaperLayout source, PaperLayout target) {
        target.setPaperSize(source.getPaperSize());
        target.setOrientation(source.getOrientation());
        target.setColumnsCount(source.getColumnsCount());
        target.setColumnGap(source.getColumnGap());
        target.setBindingLine(source.getBindingLine());
        target.setBindingPosition(source.getBindingPosition());
        target.setMarginTop(source.getMarginTop());
        target.setMarginBottom(source.getMarginBottom());
        target.setMarginLeft(source.getMarginLeft());
        target.setMarginRight(source.getMarginRight());
        target.setShowSchool(source.getShowSchool());
        target.setShowGrade(source.getShowGrade());
        target.setShowClass(source.getShowClass());
        target.setShowName(source.getShowName());
        target.setShowStudentNo(source.getShowStudentNo());
        target.setTitleFontSize(source.getTitleFontSize());
        target.setSubtitleFontSize(source.getSubtitleFontSize());
        target.setBodyFontSize(source.getBodyFontSize());
    }
}
