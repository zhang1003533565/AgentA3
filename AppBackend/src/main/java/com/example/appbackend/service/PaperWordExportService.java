package com.example.appbackend.service;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.PaperLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PaperWordExportService {
    private static final String BODY_FONT = "宋体";
    private static final String TITLE_FONT = "宋体";
    private static final String DEFAULT_VOLUME = "A卷";
    private static final List<String> TYPE_ORDER = List.of(
            "填空题", "单选题", "多选题", "判断题", "简答题", "解答题", "计算题", "证明题", "编程题");
    private static final Map<String, String> TYPE_TITLES = Map.ofEntries(
            Map.entry("单选题", "单项选择题"), Map.entry("多选题", "多项选择题"),
            Map.entry("判断题", "判断题"), Map.entry("填空题", "填空题"),
            Map.entry("简答题", "简答题"), Map.entry("解答题", "解答题"),
            Map.entry("计算题", "计算题"), Map.entry("证明题", "证明题"),
            Map.entry("编程题", "编程题"));
    private static final Pattern RICH_TEXT = Pattern.compile("(?is)<(sup|sub)>(.*?)</\\1>|<br\\s*/?>");
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
        return new ExportedWord(safeFileName(paper.getName()) + (answers ? "-答案版.docx" : ".docx"), content);
    }

    private byte[] createWord(PaperDTO.PaperVO paper, PaperLayout layout, boolean answers) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CTSectPr section = document.getDocument().getBody().isSetSectPr()
                    ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
            configureSection(section, layout, layout.getColumnsCount() == null ? 1 : layout.getColumnsCount());
            configureStyles(document, layout);
            addPageNumber(document, section);
            addExamHeader(document, paper, layout);

            List<QuestionGroup> groups = groups(paper);
            addScoreSummary(document, groups);
            // Header and score table stay full-width; only the question body flows in columns.
            addContinuousColumnSection(document, layout, 1);
            addQuestionGroups(document, groups, layout, answers);
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Word生成失败：" + exception.getMessage(), exception);
        }
    }

    private void configureStyles(XWPFDocument document, PaperLayout layout) {
        XWPFStyles styles = document.createStyles();
        XWPFStyle normal = styles.getStyle("Normal");
        if (normal == null) {
            normal = new XWPFStyle(CTStyle.Factory.newInstance());
            normal.setStyleId("Normal");
            styles.addStyle(normal);
        }
        CTRPr runProperties = normal.getCTStyle().isSetRPr() ? normal.getCTStyle().getRPr() : normal.getCTStyle().addNewRPr();
        CTFonts fonts = runProperties.addNewRFonts();
        fonts.setAscii(BODY_FONT); fonts.setHAnsi(BODY_FONT); fonts.setEastAsia(BODY_FONT);
        runProperties.addNewSz().setVal(BigInteger.valueOf(fontSize(layout.getBodyFontSize(), 12) * 2L));
    }

    private void addExamHeader(XWPFDocument document, PaperDTO.PaperVO paper, PaperLayout layout) {
        int bodySize = fontSize(layout.getBodyFontSize(), 12);
        if (Boolean.TRUE.equals(layout.getBindingLine())) addBindingNotice(document, bodySize);

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(140);
        title.setKeepNext(true);
        addRun(title, formalTitle(paper), Math.min(50, fontSize(layout.getTitleFontSize(), 50)), true, TITLE_FONT);

        XWPFParagraph examInfo = document.createParagraph();
        examInfo.setAlignment(ParagraphAlignment.CENTER);
        examInfo.setSpacingAfter(140);
        examInfo.setKeepNext(true);
        String info = "考试时间：" + value(paper.getDuration(), 0) + "分钟    满分：" + totalScore(paper) + "分";
        addRun(examInfo, info, Math.min(30, fontSize(layout.getSubtitleFontSize(), 24)), false);

        List<String> fields = studentInfoFields(layout);
        if (!fields.isEmpty()) {
            XWPFParagraph studentInfo = document.createParagraph();
            studentInfo.setAlignment(ParagraphAlignment.CENTER);
            studentInfo.setSpacingAfter(160);
            studentInfo.setKeepNext(true);
            addRun(studentInfo, String.join("    ", fields), bodySize, false);
        }
    }

    private String formalTitle(PaperDTO.PaperVO paper) {
        String subject = text(paper.getSubject()).trim();
        String category = text(paper.getCategory()).trim();
        if (subject.isEmpty() && category.isEmpty()) return text(paper.getName()) + "（" + volumeLabel(paper) + "）";
        return "《" + (subject.isEmpty() ? text(paper.getName()) : subject) + "》"
                + (category.isEmpty() ? "试题" : category) + "（" + volumeLabel(paper) + "）";
    }

    private String volumeLabel(PaperDTO.PaperVO paper) {
        // Paper目前没有卷别字段；以后增加后只需在此读取，版式构建无需调整。
        return DEFAULT_VOLUME;
    }

    private void addBindingNotice(XWPFDocument document, int bodySize) {
        XWPFParagraph notice = document.createParagraph();
        notice.setAlignment(ParagraphAlignment.CENTER);
        notice.setSpacingAfter(100);
        CTPBdr borders = notice.getCTP().addNewPPr().addNewPBdr();
        CTBorder bottom = borders.addNewBottom();
        bottom.setVal(STBorder.DASHED); bottom.setSz(BigInteger.valueOf(6)); bottom.setColor("000000");
        addRun(notice, "密封线内请勿答题    ·    装订线", Math.max(8, bodySize - 2), false);
    }

    private void addScoreSummary(XWPFDocument document, List<QuestionGroup> groups) {
        int columns = groups.size() + 2;
        XWPFTable table = document.createTable(2, columns);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        setTableBorders(table, STBorder.SINGLE, 8);
        String[] firstRow = new String[columns];
        firstRow[0] = "大题";
        for (int index = 0; index < groups.size(); index++) firstRow[index + 1] = chineseNumber(index + 1);
        firstRow[columns - 1] = "总分";
        for (int index = 0; index < columns; index++) {
            setCellText(table.getRow(0).getCell(index), firstRow[index], true, ParagraphAlignment.CENTER);
            setCellText(table.getRow(1).getCell(index), index == 0 ? "得分" : "", false, ParagraphAlignment.CENTER);
            setCellVerticalCenter(table.getRow(0).getCell(index));
            setCellVerticalCenter(table.getRow(1).getCell(index));
        }
        table.getRow(0).setHeight(420);
        table.getRow(1).setHeight(480);
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingAfter(80);
    }

    private void addQuestionGroups(XWPFDocument document, List<QuestionGroup> groups, PaperLayout layout, boolean answers) {
        int bodySize = fontSize(layout.getBodyFontSize(), 12);
        int questionNumber = 1;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            QuestionGroup group = groups.get(groupIndex);
            addGroupHeading(document, group, groupIndex, bodySize);
            for (PaperDTO.PaperQuestionVO item : group.items()) {
                addQuestion(document, item, group.type(), bodySize, answers, questionNumber++);
            }
        }
    }

    private void addContinuousColumnSection(XWPFDocument document, PaperLayout layout, int columnsCount) {
        XWPFParagraph sectionBreak = document.createParagraph();
        sectionBreak.setSpacingBefore(0);
        sectionBreak.setSpacingAfter(0);
        CTSectPr section = sectionBreak.getCTP().isSetPPr() && sectionBreak.getCTP().getPPr().isSetSectPr()
                ? sectionBreak.getCTP().getPPr().getSectPr()
                : sectionBreak.getCTP().addNewPPr().addNewSectPr();
        section.addNewType().setVal(STSectionMark.CONTINUOUS);
        CTColumns columns = section.addNewCols();
        columns.setNum(BigInteger.valueOf(Math.max(1, columnsCount)));
        columns.setSpace(twips(layout.getColumnGap()));
    }

    private void addGroupHeading(XWPFDocument document, QuestionGroup group, int groupIndex, int bodySize) {
        XWPFTable table = document.createTable(1, 3);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        table.setCellMargins(60, 60, 60, 60);
        XWPFTableRow row = table.getRow(0);
        setCellWidth(row.getCell(0), 7800);
        setCellWidth(row.getCell(1), 900);
        setCellWidth(row.getCell(2), 1500);
        clearCellBorders(row.getCell(0));
        setCellText(row.getCell(0), chineseNumber(groupIndex + 1) + "、" + group.title() + scoreDescription(group), true, ParagraphAlignment.LEFT, bodySize);
        setCellText(row.getCell(1), "得分", false, ParagraphAlignment.CENTER, Math.max(9, bodySize - 1));
        setCellText(row.getCell(2), "", false, ParagraphAlignment.CENTER, bodySize);
        setCellBorders(row.getCell(1), STBorder.SINGLE, 8);
        setCellBorders(row.getCell(2), STBorder.SINGLE, 8);
        setCellVerticalCenter(row.getCell(1)); setCellVerticalCenter(row.getCell(2));
        row.setHeight(520);
        row.setCantSplitRow(true);
    }

    private String scoreDescription(QuestionGroup group) {
        Set<Integer> scores = new LinkedHashSet<>();
        for (PaperDTO.PaperQuestionVO item : group.items()) scores.add(value(item.getScore(), 0));
        if (group.items().size() == 1) return "（" + group.totalScore() + "分）";
        if (scores.size() == 1) return "（每小题" + scores.iterator().next() + "分，共" + group.totalScore() + "分）";
        return "（共" + group.items().size() + "小题，共" + group.totalScore() + "分）";
    }

    private void addQuestion(XWPFDocument document, PaperDTO.PaperQuestionVO item, String type, int bodySize, boolean answers, int questionNumber) {
        PaperDTO.QuestionVO question = item.getQuestion();
        if (question == null) return;
        List<String> options = parseOptions(question.getOptions());
        XWPFParagraph content = document.createParagraph();
        content.setSpacingBefore(90); content.setSpacingAfter(60); content.setSpacingBetween(1.35);
        content.setKeepNext(!options.isEmpty() || answers);
        addRun(content, questionNumber + ". ", bodySize, false);
        addRichText(content, studentQuestionText(question, type, answers), bodySize);
        addRun(content, "（" + value(item.getScore(), 0) + "分）", Math.max(9, bodySize - 1), false);

        if (!options.isEmpty()) addOptions(document, options, bodySize);
        if (answers) addAnswer(document, question, type, bodySize);
        else addAnswerSpace(document, type, question.getContent(), bodySize);
    }

    private String studentQuestionText(PaperDTO.QuestionVO question, String type, boolean answers) {
        String content = text(question.getContent());
        if (isChoice(type) && !content.matches(".*[（(]\\s*[）)].*")) content += "（    ）";
        if (isFill(type) && !content.contains("__") && !content.contains("____")) {
            content += answers ? "  ____" + fallback(question.getAnswer()) + "____" : "  ____________________";
        } else if (isFill(type) && answers) {
            content += "  【答案】" + fallback(question.getAnswer());
        }
        return content;
    }

    private void addOptions(XWPFDocument document, List<String> options, int bodySize) {
        int maxLength = options.stream().mapToInt(String::length).max().orElse(0);
        int columns = maxLength <= 12 && options.size() >= 2 ? 2 : maxLength <= 28 ? 2 : 1;
        if (columns == 1) {
            for (int index = 0; index < options.size(); index++) {
                XWPFParagraph option = document.createParagraph();
                option.setIndentationLeft(420); option.setSpacingAfter(40); option.setSpacingBetween(1.15);
                addRichText(option, optionLabel(index) + ". " + options.get(index), bodySize);
            }
            return;
        }
        int rows = (int) Math.ceil(options.size() / (double) columns);
        XWPFTable table = document.createTable(rows, columns);
        table.setWidth("94%"); table.setTableAlignment(TableRowAlign.CENTER);
        removeTableBorders(table);
        for (int index = 0; index < rows * columns; index++) {
            XWPFTableCell cell = table.getRow(index / columns).getCell(index % columns);
            String value = index < options.size() ? optionLabel(index) + ". " + options.get(index) : "";
            setCellText(cell, value, false, ParagraphAlignment.LEFT, bodySize);
        }
    }

    private void addAnswer(XWPFDocument document, PaperDTO.QuestionVO question, String type, int bodySize) {
        if (!isFill(type)) {
            XWPFParagraph answer = document.createParagraph();
            answer.setIndentationLeft(240); answer.setSpacingBefore(50); answer.setSpacingAfter(40); answer.setKeepNext(true);
            addRun(answer, isChoice(type) ? "答案：" : "解：", Math.max(9, bodySize - 1), true);
            addRichText(answer, fallback(question.getAnswer()), Math.max(9, bodySize - 1));
        }
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            XWPFParagraph analysis = document.createParagraph();
            analysis.setIndentationLeft(240); analysis.setSpacingAfter(100); analysis.setSpacingBetween(1.2);
            addRun(analysis, "解析：", Math.max(9, bodySize - 1), true);
            addRichText(analysis, question.getAnalysis(), Math.max(9, bodySize - 1));
        }
    }

    private void addAnswerSpace(XWPFDocument document, String type, String content, int bodySize) {
        int lines = answerSpaceLines(type, content);
        for (int index = 0; index < lines; index++) {
            XWPFParagraph blank = document.createParagraph();
            blank.setSpacingAfter(40); blank.setSpacingBetween(1.15);
            addRun(blank, "　", bodySize, false);
        }
    }

    private int answerSpaceLines(String type, String content) {
        if (isChoice(type) || isFill(type) || type.contains("判断")) return 0;
        if (type.contains("证明")) return 10;
        if (type.contains("计算") || type.contains("解答") || type.contains("编程")) return 7;
        if (type.contains("简答")) return 4;
        return text(content).length() > 120 ? 4 : 2;
    }

    private List<QuestionGroup> groups(PaperDTO.PaperVO paper) {
        Map<String, List<PaperDTO.PaperQuestionVO>> grouped = new LinkedHashMap<>();
        for (PaperDTO.PaperQuestionVO item : sortedQuestions(paper)) {
            String type = item.getQuestion() == null || item.getQuestion().getQuestionType() == null ? "其他题型" : item.getQuestion().getQuestionType();
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>()).add(item);
        }
        return grouped.entrySet().stream().sorted(Comparator.comparingInt(entry -> typeRank(entry.getKey())))
                .map(entry -> new QuestionGroup(entry.getKey(), TYPE_TITLES.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue(),
                        entry.getValue().stream().mapToInt(item -> value(item.getScore(), 0)).sum())).toList();
    }

    private void addPageNumber(XWPFDocument document, CTSectPr section) {
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(document, section);
        XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        addRun(paragraph, "-", 10, false);
        XWPFRun fieldRun = paragraph.createRun();
        CTFldChar begin = fieldRun.getCTR().addNewFldChar(); begin.setFldCharType(STFldCharType.BEGIN);
        XWPFRun instructionRun = paragraph.createRun(); instructionRun.getCTR().addNewInstrText().setStringValue(" PAGE ");
        XWPFRun endRun = paragraph.createRun(); endRun.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        addRun(paragraph, "-", 10, false);
    }

    private void configureSection(CTSectPr section, PaperLayout layout, int columnsCount) {
        BigInteger width = "A3".equals(layout.getPaperSize()) ? A3_WIDTH : A4_WIDTH;
        BigInteger height = "A3".equals(layout.getPaperSize()) ? A3_HEIGHT : A4_HEIGHT;
        boolean landscape = "landscape".equals(layout.getOrientation());
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(landscape ? height : width); pageSize.setH(landscape ? width : height);
        pageSize.setOrient(landscape ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(twips(layout.getMarginTop())); margins.setBottom(twips(layout.getMarginBottom()));
        margins.setLeft(twips(layout.getMarginLeft())); margins.setRight(twips(layout.getMarginRight()));
        margins.setHeader(BigInteger.valueOf(600)); margins.setFooter(BigInteger.valueOf(600));
        margins.setGutter(Boolean.TRUE.equals(layout.getBindingLine()) ? twips(new BigDecimal("1.0")) : BigInteger.ZERO);
        CTColumns columns = section.isSetCols() ? section.getCols() : section.addNewCols();
        columns.setNum(BigInteger.valueOf(columnsCount)); columns.setSpace(twips(layout.getColumnGap()));
    }

    private void addRichText(XWPFParagraph paragraph, String value, int size) {
        String normalized = text(value).replace("\r\n", "\n").replace("\r", "\n");
        Matcher matcher = RICH_TEXT.matcher(normalized);
        int position = 0;
        while (matcher.find()) {
            addPlainWithBreaks(paragraph, normalized.substring(position, matcher.start()), size, false, false);
            if (matcher.group().toLowerCase(Locale.ROOT).startsWith("<br")) paragraph.createRun().addBreak();
            else addPlainWithBreaks(paragraph, matcher.group(2), size, "sup".equalsIgnoreCase(matcher.group(1)), "sub".equalsIgnoreCase(matcher.group(1)));
            position = matcher.end();
        }
        addPlainWithBreaks(paragraph, normalized.substring(position), size, false, false);
    }

    private void addPlainWithBreaks(XWPFParagraph paragraph, String value, int size, boolean superscript, boolean subscript) {
        String clean = value.replaceAll("(?is)<[^>]+>", "").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        String[] lines = clean.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) paragraph.createRun().addBreak();
            XWPFRun run = addRun(paragraph, lines[index], size, false);
            if (superscript) run.setSubscript(VerticalAlign.SUPERSCRIPT);
            if (subscript) run.setSubscript(VerticalAlign.SUBSCRIPT);
        }
    }

    private XWPFRun addRun(XWPFParagraph paragraph, String value, int size, boolean bold) { return addRun(paragraph, value, size, bold, BODY_FONT); }
    private XWPFRun addRun(XWPFParagraph paragraph, String value, int size, boolean bold, String font) {
        XWPFRun run = paragraph.createRun(); run.setText(text(value)); run.setFontFamily(font);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia); run.setFontSize(size); run.setBold(bold); run.setColor("000000");
        return run;
    }

    private void setCellText(XWPFTableCell cell, String text, boolean bold, ParagraphAlignment alignment) { setCellText(cell, text, bold, alignment, 11); }
    private void setCellText(XWPFTableCell cell, String text, boolean bold, ParagraphAlignment alignment, int size) {
        cell.removeParagraph(0); XWPFParagraph paragraph = cell.addParagraph(); paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0); paragraph.setSpacingAfter(0); addRun(paragraph, text, size, bold);
    }

    private void setCellWidth(XWPFTableCell cell, int width) { CTTblWidth tcW = cell.getCTTc().addNewTcPr().addNewTcW(); tcW.setType(STTblWidth.DXA); tcW.setW(BigInteger.valueOf(width)); }
    private void setCellVerticalCenter(XWPFTableCell cell) { cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER); }
    private void setTableBorders(XWPFTable table, STBorder.Enum style, int size) {
        CTTblBorders borders = table.getCTTbl().getTblPr().isSetTblBorders() ? table.getCTTbl().getTblPr().getTblBorders() : table.getCTTbl().getTblPr().addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewBottom(), borders.addNewLeft(), borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) { border.setVal(style); border.setSz(BigInteger.valueOf(size)); border.setColor("000000"); }
    }
    private void removeTableBorders(XWPFTable table) { setTableBorders(table, STBorder.NIL, 0); }
    private void clearCellBorders(XWPFTableCell cell) { setCellBorders(cell, STBorder.NIL, 0); }
    private void setCellBorders(XWPFTableCell cell, STBorder.Enum style, int size) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = properties.isSetTcBorders() ? properties.getTcBorders() : properties.addNewTcBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewBottom(), borders.addNewLeft(), borders.addNewRight())) { border.setVal(style); border.setSz(BigInteger.valueOf(size)); border.setColor("000000"); }
    }

    private List<PaperDTO.PaperQuestionVO> sortedQuestions(PaperDTO.PaperVO paper) {
        List<PaperDTO.PaperQuestionVO> questions = new ArrayList<>(paper.getQuestions() == null ? List.of() : paper.getQuestions());
        questions.sort(Comparator.comparing(item -> item.getQuestionOrder() == null ? Integer.MAX_VALUE : item.getQuestionOrder()));
        return questions;
    }

    private List<String> parseOptions(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) return list.stream().map(String::valueOf).toList();
            if (parsed instanceof Map<?, ?> map) return map.values().stream().map(String::valueOf).toList();
        } catch (Exception ignored) { return Arrays.stream(raw.split("\\R")).filter(value -> !value.isBlank()).toList(); }
        return List.of();
    }

    private List<String> studentInfoFields(PaperLayout layout) {
        if (Boolean.FALSE.equals(layout.getShowStudentInfo())) return List.of();
        if (layout.getStudentFields() != null && !layout.getStudentFields().isBlank()) {
            return Arrays.stream(layout.getStudentFields().split(",")).map(String::trim).filter(value -> !value.isEmpty()).map(this::studentFieldLabel).toList();
        }
        List<String> fields = new ArrayList<>();
        if (Boolean.TRUE.equals(layout.getShowSchool())) fields.add("学校 ____________");
        if (Boolean.TRUE.equals(layout.getShowGrade())) fields.add("年级 ____________");
        if (Boolean.TRUE.equals(layout.getShowClass())) fields.add("班级 ____________");
        if (Boolean.TRUE.equals(layout.getShowName())) fields.add("姓名 ____________");
        if (Boolean.TRUE.equals(layout.getShowStudentNo())) fields.add("学号 ____________");
        return fields;
    }

    private String studentFieldLabel(String field) { return switch (field) { case "school" -> "学校 ____________"; case "grade" -> "年级 ____________"; case "class" -> "班级 ____________"; case "name" -> "姓名 ____________"; case "studentNo" -> "学号 ____________"; default -> field; }; }
    private int totalScore(PaperDTO.PaperVO paper) { return paper.getTotalScore() != null ? paper.getTotalScore() : sortedQuestions(paper).stream().mapToInt(item -> value(item.getScore(), 0)).sum(); }
    private int typeRank(String type) { int index = TYPE_ORDER.indexOf(type); return index < 0 ? TYPE_ORDER.size() : index; }
    private boolean isChoice(String type) { return type != null && (type.contains("选择") || type.contains("单选") || type.contains("多选")); }
    private boolean isFill(String type) { return type != null && type.contains("填空"); }
    private String chineseNumber(int index) { String[] values = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"}; return index >= 1 && index <= values.length ? values[index - 1] : String.valueOf(index); }
    private String optionLabel(int index) { return String.valueOf((char) ('A' + index)); }
    private int fontSize(Integer value, int fallback) { return value == null ? fallback : Math.max(8, Math.min(72, value)); }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String fallback(String value) { return value == null || value.isBlank() ? "暂无" : value; }
    private String text(String value) { return value == null ? "" : value; }
    private BigInteger twips(BigDecimal centimeters) { return centimeters == null ? BigInteger.ZERO : BigInteger.valueOf(Math.round(centimeters.doubleValue() * 1440d / 2.54d)); }
    private String safeFileName(String value) { String result = text(value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim(); return result.isBlank() ? "试卷" : result; }

    private record QuestionGroup(String type, String title, List<PaperDTO.PaperQuestionVO> items, int totalScore) {}
    public record ExportedWord(String fileName, byte[] content) {}
}
