package com.example.appbackend.service;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.PaperLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColumns;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperWordExportService {
    private static final String FONT_FAMILY = "宋体";
    private static final List<String> TYPE_ORDER = List.of("单选题", "多选题", "判断题", "填空题", "简答题", "编程题");
    private static final Map<String, String> TYPE_TITLES = Map.of(
            "单选题", "单项选择题",
            "多选题", "多项选择题",
            "判断题", "判断题",
            "填空题", "填空题",
            "简答题", "简答题",
            "编程题", "编程题"
    );
    private static final BigInteger A4_WIDTH = BigInteger.valueOf(11906);
    private static final BigInteger A4_HEIGHT = BigInteger.valueOf(16838);
    private static final BigInteger A3_WIDTH = BigInteger.valueOf(16838);
    private static final BigInteger A3_HEIGHT = BigInteger.valueOf(23811);

    private final PaperService paperService;
    private final PaperLayoutService layoutService;
    private final ObjectMapper objectMapper;

    public PaperWordExportService(PaperService paperService, PaperLayoutService layoutService, ObjectMapper objectMapper) {
        this.paperService = paperService;
        this.layoutService = layoutService;
        this.objectMapper = objectMapper;
    }

    public ExportedWord export(Long paperId, Long userId, boolean answers) {
        PaperDTO.PaperVO paper = paperService.getPaper(paperId, userId);
        PaperLayout layout = layoutService.get(paperId, userId);
        byte[] content = createWord(paper, layout, answers);
        String suffix = answers ? "-答案版.docx" : ".docx";
        return new ExportedWord(safeFileName(paper.getName()) + suffix, content);
    }

    private byte[] createWord(PaperDTO.PaperVO paper, PaperLayout layout, boolean answers) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int columns = layout.getColumnsCount() == null ? 1 : layout.getColumnsCount();
            CTSectPr bodySection = document.getDocument().getBody().isSetSectPr()
                    ? document.getDocument().getBody().getSectPr()
                    : document.getDocument().getBody().addNewSectPr();
            configureSection(bodySection, layout, columns);
            addHeader(document, paper, layout);

            if (columns == 2) addContinuousSectionBreak(document, layout);
            addQuestions(document, paper, layout, answers);

            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Word生成失败：" + exception.getMessage(), exception);
        }
    }

    private void addHeader(XWPFDocument document, PaperDTO.PaperVO paper, PaperLayout layout) {
        int bodySize = fontSize(layout.getBodyFontSize(), 12);
        if (Boolean.TRUE.equals(layout.getShowSchool())) {
            XWPFParagraph school = document.createParagraph();
            school.setSpacingAfter(120);
            addRun(school, "学校：____________________________", bodySize, false);
        }

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(160);
        addRun(title, text(paper.getName()), fontSize(layout.getTitleFontSize(), 24), true);

        XWPFParagraph examInfo = document.createParagraph();
        examInfo.setAlignment(ParagraphAlignment.CENTER);
        examInfo.setSpacingAfter(180);
        String info = "科目：" + text(paper.getSubject())
                + "    考试时间：" + (paper.getDuration() == null ? 0 : paper.getDuration()) + "分钟"
                + "    总分：" + totalScore(paper) + "分";
        addRun(examInfo, info, fontSize(layout.getSubtitleFontSize(), 18), false);

        List<String> fields = studentInfoFields(layout);
        if (!fields.isEmpty()) {
            XWPFParagraph studentInfo = document.createParagraph();
            studentInfo.setAlignment(ParagraphAlignment.CENTER);
            studentInfo.setSpacingAfter(240);
            addRun(studentInfo, String.join("    ", fields), bodySize, false);
        }
    }

    private void addContinuousSectionBreak(XWPFDocument document, PaperLayout layout) {
        XWPFParagraph sectionBreak = document.createParagraph();
        sectionBreak.setSpacingAfter(0);
        CTPPr paragraphProperties = sectionBreak.getCTP().isSetPPr()
                ? sectionBreak.getCTP().getPPr()
                : sectionBreak.getCTP().addNewPPr();
        CTSectPr firstSection = paragraphProperties.addNewSectPr();
        configureSection(firstSection, layout, 1);
        firstSection.addNewType().setVal(STSectionMark.CONTINUOUS);
    }

    private void addQuestions(XWPFDocument document, PaperDTO.PaperVO paper, PaperLayout layout, boolean answers) {
        int bodySize = fontSize(layout.getBodyFontSize(), 12);
        Map<String, List<PaperDTO.PaperQuestionVO>> grouped = new LinkedHashMap<>();
        sortedQuestions(paper).forEach(item -> {
            String type = item.getQuestion() == null || item.getQuestion().getQuestionType() == null
                    ? "其他题型" : item.getQuestion().getQuestionType();
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>()).add(item);
        });

        List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groups = new ArrayList<>(grouped.entrySet());
        groups.sort(Comparator.comparingInt(entry -> typeRank(entry.getKey())));
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            Map.Entry<String, List<PaperDTO.PaperQuestionVO>> group = groups.get(groupIndex);
            int groupScore = group.getValue().stream()
                    .mapToInt(item -> item.getScore() == null ? 0 : item.getScore())
                    .sum();
            String heading = chineseNumber(groupIndex + 1) + "、"
                    + TYPE_TITLES.getOrDefault(group.getKey(), group.getKey())
                    + "（共" + group.getValue().size() + "题，共" + groupScore + "分）";
            XWPFParagraph groupTitle = document.createParagraph();
            groupTitle.setSpacingBefore(140);
            groupTitle.setSpacingAfter(120);
            groupTitle.setKeepNext(true);
            addRun(groupTitle, heading, bodySize, true);

            for (PaperDTO.PaperQuestionVO item : group.getValue()) {
                addQuestion(document, item, bodySize, answers);
            }
        }
    }

    private void addQuestion(XWPFDocument document, PaperDTO.PaperQuestionVO item, int bodySize, boolean answers) {
        PaperDTO.QuestionVO question = item.getQuestion();
        if (question == null) return;
        List<String> options = parseOptions(question.getOptions());

        XWPFParagraph content = document.createParagraph();
        content.setSpacingAfter(60);
        content.setSpacingBetween(1.25);
        content.setKeepNext(!options.isEmpty() || answers);
        addRun(content, (item.getQuestionOrder() == null ? "" : item.getQuestionOrder() + ". ")
                + text(question.getContent())
                + "（" + (item.getScore() == null ? 0 : item.getScore()) + "分）", bodySize, false);

        for (int index = 0; index < options.size(); index++) {
            XWPFParagraph option = document.createParagraph();
            option.setIndentationLeft(360);
            option.setSpacingAfter(40);
            option.setSpacingBetween(1.15);
            option.setKeepNext(index < options.size() - 1 || answers);
            addRun(option, optionLabel(index) + ". " + options.get(index), bodySize, false);
        }

        if (answers) {
            XWPFParagraph answer = document.createParagraph();
            answer.setIndentationLeft(180);
            answer.setSpacingBefore(60);
            answer.setSpacingAfter(40);
            answer.setKeepNext(true);
            addRun(answer, "【答案】", Math.max(8, bodySize - 1), true);
            addRun(answer, fallback(question.getAnswer()), Math.max(8, bodySize - 1), false);

            XWPFParagraph analysis = document.createParagraph();
            analysis.setIndentationLeft(180);
            analysis.setSpacingAfter(160);
            addRun(analysis, "【解析】", Math.max(8, bodySize - 1), true);
            addRun(analysis, fallback(question.getAnalysis()), Math.max(8, bodySize - 1), false);
        } else {
            content.setSpacingAfter(140);
        }
    }

    private void configureSection(CTSectPr section, PaperLayout layout, int columnsCount) {
        BigInteger width = "A3".equals(layout.getPaperSize()) ? A3_WIDTH : A4_WIDTH;
        BigInteger height = "A3".equals(layout.getPaperSize()) ? A3_HEIGHT : A4_HEIGHT;
        boolean landscape = "landscape".equals(layout.getOrientation());

        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(landscape ? height : width);
        pageSize.setH(landscape ? width : height);
        pageSize.setOrient(landscape ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);

        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(twips(layout.getMarginTop()));
        margins.setBottom(twips(layout.getMarginBottom()));
        margins.setLeft(twips(layout.getMarginLeft()));
        margins.setRight(twips(layout.getMarginRight()));
        margins.setHeader(BigInteger.valueOf(708));
        margins.setFooter(BigInteger.valueOf(708));
        margins.setGutter(BigInteger.ZERO);

        CTColumns columns = section.isSetCols() ? section.getCols() : section.addNewCols();
        columns.setNum(BigInteger.valueOf(columnsCount));
        columns.setSpace(twips(layout.getColumnGap()));
    }

    private XWPFRun addRun(XWPFParagraph paragraph, String value, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setText(value == null ? "" : value);
        run.setFontFamily(FONT_FAMILY);
        run.setFontFamily(FONT_FAMILY, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
        return run;
    }

    private List<PaperDTO.PaperQuestionVO> sortedQuestions(PaperDTO.PaperVO paper) {
        List<PaperDTO.PaperQuestionVO> questions = new ArrayList<>(
                paper.getQuestions() == null ? List.of() : paper.getQuestions());
        questions.sort(Comparator.comparing(
                item -> item.getQuestionOrder() == null ? Integer.MAX_VALUE : item.getQuestionOrder()));
        return questions;
    }

    private List<String> parseOptions(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Object value = objectMapper.readValue(raw, Object.class);
            if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
            if (value instanceof Map<?, ?> map) return map.values().stream().map(String::valueOf).toList();
        } catch (Exception ignored) {
            return Arrays.stream(raw.split("\\R")).filter(value -> !value.isBlank()).toList();
        }
        return List.of();
    }

    private List<String> studentInfoFields(PaperLayout layout) {
        List<String> fields = new ArrayList<>();
        if (Boolean.TRUE.equals(layout.getShowSchool())) fields.add("学校 ____________");
        if (Boolean.TRUE.equals(layout.getShowGrade())) fields.add("年级 ____________");
        if (Boolean.TRUE.equals(layout.getShowClass())) fields.add("班级 ____________");
        if (Boolean.TRUE.equals(layout.getShowName())) fields.add("姓名 ____________");
        if (Boolean.TRUE.equals(layout.getShowStudentNo())) fields.add("学号 ____________");
        return fields;
    }

    private int totalScore(PaperDTO.PaperVO paper) {
        if (paper.getTotalScore() != null) return paper.getTotalScore();
        return sortedQuestions(paper).stream()
                .mapToInt(item -> item.getScore() == null ? 0 : item.getScore())
                .sum();
    }

    private int typeRank(String type) {
        int index = TYPE_ORDER.indexOf(type);
        return index < 0 ? TYPE_ORDER.size() : index;
    }

    private String chineseNumber(int index) {
        String[] values = {"一", "二", "三", "四", "五", "六", "七", "八"};
        return index >= 1 && index <= values.length ? values[index - 1] : String.valueOf(index);
    }

    private String optionLabel(int index) { return String.valueOf((char) ('A' + index)); }
    private int fontSize(Integer value, int fallback) { return value == null ? fallback : value; }
    private String fallback(String value) { return value == null || value.isBlank() ? "暂无" : value; }
    private String text(String value) { return value == null ? "" : value; }

    private BigInteger twips(BigDecimal centimeters) {
        if (centimeters == null) return BigInteger.ZERO;
        return BigInteger.valueOf(Math.round(centimeters.doubleValue() * 1440d / 2.54d));
    }

    private String safeFileName(String value) {
        String result = text(value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return result.isBlank() ? "试卷" : result;
    }

    public record ExportedWord(String fileName, byte[] content) {}
}
