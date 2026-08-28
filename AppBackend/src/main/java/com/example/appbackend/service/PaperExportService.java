package com.example.appbackend.service;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.PaperLayout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.*;

@Service
public class PaperExportService {
    private static final float CM_TO_POINT = 28.3465f;
    private static final List<String> TYPE_ORDER = List.of("单选题", "多选题", "判断题", "填空题", "简答题", "编程题");
    private static final Map<String, String> TYPE_TITLES = Map.of(
            "单选题", "单项选择题",
            "多选题", "多项选择题",
            "判断题", "判断题",
            "填空题", "填空题",
            "简答题", "简答题",
            "编程题", "编程题"
    );

    private final PaperService paperService;
    private final PaperLayoutService layoutService;
    private final ObjectMapper objectMapper;

    public PaperExportService(PaperService paperService, PaperLayoutService layoutService, ObjectMapper objectMapper) {
        this.paperService = paperService;
        this.layoutService = layoutService;
        this.objectMapper = objectMapper;
    }

    public ExportedPdf export(Long paperId, Long userId, boolean answers) {
        PaperDTO.PaperVO paper = paperService.getPaper(paperId, userId);
        PaperLayout layout = layoutService.get(paperId, userId);
        byte[] bytes = createPdf(paper, layout, answers);
        String suffix = answers ? "-答案版.pdf" : ".pdf";
        return new ExportedPdf(safeFileName(paper.getName()) + suffix, bytes);
    }

    private byte[] createPdf(PaperDTO.PaperVO paper, PaperLayout layout, boolean answers) {
        Rectangle pageSize = "A3".equals(layout.getPaperSize()) ? PageSize.A3 : PageSize.A4;
        if ("landscape".equals(layout.getOrientation())) pageSize = pageSize.rotate();

        if (isSealedA3Layout(layout)) {
            return createSealedA3Pdf(paper, layout, answers, pageSize);
        }

        float marginTop = points(layout.getMarginTop());
        float marginBottom = points(layout.getMarginBottom());
        float marginLeft = points(layout.getMarginLeft());
        float marginRight = points(layout.getMarginRight());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(pageSize, marginLeft, marginRight, marginTop, marginBottom);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            BaseFont baseFont = chineseFont();
            writer.setPageEvent(new PaperPageEvent(baseFont, layout, false));
            document.open();

            Font titleFont = new Font(baseFont, layout.getTitleFontSize(), Font.BOLD);
            Font subtitleFont = new Font(baseFont, layout.getSubtitleFontSize(), Font.NORMAL);
            Font bodyFont = new Font(baseFont, layout.getBodyFontSize(), Font.NORMAL);
            Font bodyBold = new Font(baseFont, layout.getBodyFontSize(), Font.BOLD);
            Font answerFont = new Font(baseFont, Math.max(8, layout.getBodyFontSize() - 1), Font.NORMAL, new Color(45, 73, 112));

            if (Boolean.TRUE.equals(layout.getShowSchool())) {
                Paragraph school = new Paragraph("学校：____________________________", bodyFont);
                school.setAlignment(Element.ALIGN_LEFT);
                school.setSpacingAfter(6f);
                document.add(school);
            }

            Paragraph title = new Paragraph(formalTitle(paper), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(8f);
            document.add(title);

            PdfPTable examInfo = new PdfPTable(new float[]{60f, 40f});
            examInfo.setWidthPercentage(100f);
            examInfo.setSpacingAfter(12f);
            PdfPCell subjectCell = infoCell("科目：" + text(paper.getSubject()), subtitleFont);
            subjectCell.setColspan(2);
            examInfo.addCell(subjectCell);
            examInfo.addCell(infoCell("考试时间：" + (paper.getDuration() == null ? 0 : paper.getDuration()) + "分钟", subtitleFont));
            examInfo.addCell(infoCell("总分：" + totalScore(paper) + "分", subtitleFont));
            document.add(examInfo);

            List<String> studentFields = studentInfoFields(layout);
            if (!studentFields.isEmpty()) {
                document.add(studentInfoTable(studentFields, bodyFont));
            }

            List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> summaryGroups = groupedQuestions(paper);
            document.add(scoreSummaryTable(summaryGroups, bodyFont));

            float top = writer.getVerticalPosition(true);
            ColumnText columns = new ColumnText(writer.getDirectContent());
            addQuestionElements(columns, paper, answers, bodyFont, bodyBold, answerFont, false);
            flowColumns(columns, writer, document, layout, top, marginBottom);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            if (document.isOpen()) document.close();
            throw new IllegalStateException("PDF生成失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * Generates the standard school examination sheet: one physical A3 sheet contains two
     * logical pages, with a sealing area on the left and a divider between the pages.
     */
    private byte[] createSealedA3Pdf(PaperDTO.PaperVO paper, PaperLayout layout, boolean answers,
                                     Rectangle pageSize) {
        final float marginLeft = 110f;
        final float marginRight = 22f;
        final float marginTop = 24f;
        final float marginBottom = 28f;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(pageSize, marginLeft, marginRight, marginTop, marginBottom);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            BaseFont baseFont = chineseFont();
            writer.setPageEvent(new PaperPageEvent(baseFont, layout, true));
            document.open();

            Font titleFont = new Font(baseFont, 17f, Font.BOLD);
            Font subtitleFont = new Font(baseFont, 7.5f, Font.NORMAL);
            Font bodyFont = new Font(baseFont, 8.2f, Font.NORMAL);
            Font bodyBold = new Font(baseFont, 8.2f, Font.BOLD);
            Font answerFont = new Font(baseFont, 7.8f, Font.NORMAL, new Color(45, 73, 112));

            ColumnText content = new ColumnText(writer.getDirectContent());
            Paragraph title = new Paragraph(compactTitle(paper), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setLeading(20f);
            title.setSpacingAfter(5f);
            content.addElement(title);

            Paragraph meta = new Paragraph("考试时间：" + (paper.getDuration() == null ? 0 : paper.getDuration())
                    + "分钟    满分：" + totalScore(paper) + "分", subtitleFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(4f);
            content.addElement(meta);

            List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groups = groupedQuestions(paper);
            content.addElement(compactScoreSummaryTable(groups, subtitleFont));
            addQuestionElements(content, paper, answers, bodyFont, bodyBold, answerFont, true);
            flowSealedA3Columns(content, document, 18f);

            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            if (document.isOpen()) document.close();
            throw new IllegalStateException("PDF生成失败：" + exception.getMessage(), exception);
        }
    }

    private boolean isSealedA3Layout(PaperLayout layout) {
        return "A3".equals(layout.getPaperSize())
                && "landscape".equals(layout.getOrientation())
                && Objects.equals(layout.getColumnsCount(), 2)
                && Boolean.TRUE.equals(layout.getBindingLine());
    }

    private PdfPTable compactScoreSummaryTable(List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groups,
                                                Font font) {
        PdfPTable table = new PdfPTable(groups.size() + 2);
        table.setWidthPercentage(96f);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.setSpacingAfter(7f);
        table.addCell(compactTableCell("题号", font));
        for (int index = 0; index < groups.size(); index++) {
            table.addCell(compactTableCell(chineseNumber(index + 1), font));
        }
        table.addCell(compactTableCell("总分", font));
        table.addCell(compactTableCell("得分", font));
        for (int index = 0; index < groups.size() + 1; index++) {
            table.addCell(compactTableCell("", font));
        }
        return table;
    }

    private PdfPCell compactTableCell(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderWidth(0.45f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(1.5f);
        cell.setMinimumHeight(13f);
        return cell;
    }

    private void flowSealedA3Columns(ColumnText content, Document document, float gap)
            throws DocumentException {
        float left = document.left();
        float right = document.right();
        float bottom = document.bottom() + 10f;
        float top = document.top();
        float columnWidth = (right - left - gap) / 2f;
        int column = 0;

        while (true) {
            float x1 = left + column * (columnWidth + gap);
            content.setSimpleColumn(x1, bottom, x1 + columnWidth, top);
            int status = content.go();
            if (!ColumnText.hasMoreText(status)) break;
            column++;
            if (column >= 2) {
                document.newPage();
                column = 0;
            }
        }
    }

    private void addQuestionElements(ColumnText columns, PaperDTO.PaperVO paper, boolean answers,
                                     Font bodyFont, Font bodyBold, Font answerFont, boolean generousAnswerSpace) {
        List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groups = groupedQuestions(paper);
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            Map.Entry<String, List<PaperDTO.PaperQuestionVO>> group = groups.get(groupIndex);
            int score = group.getValue().stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).sum();
            String heading = chineseNumber(groupIndex + 1) + "、" + TYPE_TITLES.getOrDefault(group.getKey(), group.getKey())
                    + "（共" + group.getValue().size() + "题，共" + score + "分）";
            PdfPTable headingTable = new PdfPTable(new float[]{22f, 78f});
            headingTable.setWidthPercentage(100f);
            headingTable.setKeepTogether(true);
            headingTable.setSpacingBefore(4f);
            headingTable.setSpacingAfter(3f);
            headingTable.addCell(reviewerScoreCell(bodyFont));
            headingTable.addCell(borderlessCell(heading, bodyBold, Element.ALIGN_LEFT));
            columns.addElement(headingTable);

            int questionNo = 1;
            for (PaperDTO.PaperQuestionVO item : group.getValue()) {
                PaperDTO.QuestionVO question = item.getQuestion();
                if (question == null) continue;
                Paragraph content = new Paragraph(questionNo++ + ". " + text(question.getContent())
                        + "（" + (item.getScore() == null ? 0 : item.getScore()) + "分）", bodyFont);
                content.setLeading(0, 1.45f);
                content.setSpacingAfter(3f);
                columns.addElement(content);

                List<String> options = parseOptions(question.getOptions());
                for (int index = 0; index < options.size(); index++) {
                    Paragraph option = new Paragraph((char) ('A' + index) + ". " + options.get(index), bodyFont);
                    option.setIndentationLeft(18f);
                    option.setLeading(0, 1.35f);
                    columns.addElement(option);
                }

                if (answers) {
                    Paragraph answer = new Paragraph("正确答案：" + fallback(question.getAnswer()), answerFont);
                    answer.setIndentationLeft(10f);
                    answer.setSpacingBefore(4f);
                    columns.addElement(answer);
                    Paragraph analysis = new Paragraph("答案解析：" + fallback(question.getAnalysis()), answerFont);
                    analysis.setIndentationLeft(10f);
                    analysis.setSpacingAfter(8f);
                    columns.addElement(analysis);
                } else {
                    Paragraph gap = new Paragraph(" ", bodyFont);
                    gap.setSpacingAfter(generousAnswerSpace ? answerSpace(question.getQuestionType()) : 5f);
                    columns.addElement(gap);
                }
            }
        }
    }

    private float answerSpace(String questionType) {
        String type = text(questionType);
        if (type.contains("编程")) return 92f;
        if (type.contains("解答") || type.contains("证明")) return 68f;
        if (type.contains("简答")) return 38f;
        if (type.contains("填空")) return 28f;
        return 14f;
    }

    private List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groupedQuestions(PaperDTO.PaperVO paper) {
        Map<String, List<PaperDTO.PaperQuestionVO>> grouped = new LinkedHashMap<>();
        sortedQuestions(paper).forEach(item -> {
            String type = item.getQuestion() == null || item.getQuestion().getQuestionType() == null
                    ? "其他题型" : item.getQuestion().getQuestionType();
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>()).add(item);
        });
        List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groups = new ArrayList<>(grouped.entrySet());
        groups.sort(Comparator.comparingInt(entry -> typeRank(entry.getKey())));
        return groups;
    }

    private PdfPTable scoreSummaryTable(List<Map.Entry<String, List<PaperDTO.PaperQuestionVO>>> groups, Font font) {
        PdfPTable table = new PdfPTable(groups.size() + 2);
        table.setWidthPercentage(100f);
        table.setSpacingAfter(10f);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(infoCell("大题", font));
        for (int index = 0; index < groups.size(); index++) table.addCell(infoCell(chineseNumber(index + 1), font));
        table.addCell(infoCell("总分", font));
        table.addCell(infoCell("得分", font));
        for (int index = 0; index < groups.size() + 1; index++) table.addCell(infoCell("", font));
        return table;
    }

    private PdfPCell borderlessCell(String text, Font font, int alignment) {
        PdfPCell cell = infoCell(text, font);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private PdfPCell scoreCell(String text, Font font) {
        PdfPCell cell = infoCell(text, font);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderWidth(0.45f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell reviewerScoreCell(Font font) {
        PdfPTable box = new PdfPTable(new float[]{45f, 55f});
        box.addCell(scoreCell("阅卷人", font));
        box.addCell(scoreCell("", font));
        box.addCell(scoreCell("得分", font));
        box.addCell(scoreCell("", font));
        PdfPCell wrapper = new PdfPCell(box);
        wrapper.setBorder(Rectangle.NO_BORDER);
        wrapper.setPadding(0f);
        wrapper.setPaddingRight(6f);
        wrapper.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return wrapper;
    }

    private void flowColumns(ColumnText content, PdfWriter writer, Document document, PaperLayout layout,
                             float firstTop, float marginBottom) throws DocumentException {
        int columnCount = layout.getColumnsCount() == null ? 1 : layout.getColumnsCount();
        float gap = points(layout.getColumnGap());
        float left = document.left();
        float right = document.right();
        float bottom = marginBottom + 18f;
        float columnWidth = (right - left - gap * (columnCount - 1)) / columnCount;
        float top = firstTop;
        int column = 0;

        while (true) {
            float x1 = left + column * (columnWidth + gap);
            content.setSimpleColumn(x1, bottom, x1 + columnWidth, top);
            int status = content.go();
            if (!ColumnText.hasMoreText(status)) break;
            column++;
            if (column >= columnCount) {
                document.newPage();
                column = 0;
                top = document.top();
            }
        }
    }

    private List<PaperDTO.PaperQuestionVO> sortedQuestions(PaperDTO.PaperVO paper) {
        List<PaperDTO.PaperQuestionVO> questions = new ArrayList<>(paper.getQuestions() == null ? List.of() : paper.getQuestions());
        questions.sort(Comparator.comparing(item -> item.getQuestionOrder() == null ? Integer.MAX_VALUE : item.getQuestionOrder()));
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

    private BaseFont chineseFont() throws Exception {
        List<String> candidates = new ArrayList<>();
        String windows = System.getenv("WINDIR");
        if (windows != null) {
            candidates.add(windows + "\\Fonts\\simsun.ttc,0");
            candidates.add(windows + "\\Fonts\\msyh.ttc,0");
            candidates.add(windows + "\\Fonts\\simhei.ttf");
        }
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0");
        candidates.add("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0");
        for (String candidate : candidates) {
            String path = candidate.replaceFirst(",\\d+$", "");
            if (new File(path).isFile()) {
                return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        throw new IllegalStateException("未找到可用的中文字体，请安装宋体、微软雅黑或Noto Sans CJK");
    }

    private List<String> studentInfoFields(PaperLayout layout) {
        if (Boolean.FALSE.equals(layout.getShowStudentInfo())) return List.of();
        if (layout.getStudentFields() != null && !layout.getStudentFields().isBlank()) {
            return Arrays.stream(layout.getStudentFields().split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(this::studentFieldLabel).toList();
        }
        List<String> fields = new ArrayList<>();
        if (Boolean.TRUE.equals(layout.getShowSchool())) fields.add("学校 ____________");
        if (Boolean.TRUE.equals(layout.getShowGrade())) fields.add("年级 ____________");
        if (Boolean.TRUE.equals(layout.getShowClass())) fields.add("班级 ____________");
        if (Boolean.TRUE.equals(layout.getShowName())) fields.add("姓名 ____________");
        if (Boolean.TRUE.equals(layout.getShowStudentNo())) fields.add("学号 ____________");
        return fields;
    }

    private String studentFieldLabel(String field) {
        return switch (field) { case "school" -> "学校 ____________"; case "grade" -> "年级 ____________"; case "class" -> "班级 ____________"; case "name" -> "姓名 ____________"; case "studentNo" -> "学号 ____________"; default -> field; };
    }

    private PdfPTable studentInfoTable(List<String> fields, Font font) {
        int columns = Math.min(3, fields.size());
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(92f);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.setSpacingAfter(14f);
        for (String field : fields) table.addCell(infoCell(field, font));
        int remainder = fields.size() % columns;
        for (int index = remainder; remainder != 0 && index < columns; index++) table.addCell(infoCell("", font));
        return table;
    }

    private PdfPCell infoCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(2f);
        return cell;
    }

    private int totalScore(PaperDTO.PaperVO paper) {
        if (paper.getTotalScore() != null) return paper.getTotalScore();
        return sortedQuestions(paper).stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).sum();
    }

    private int typeRank(String type) {
        int index = TYPE_ORDER.indexOf(type);
        return index < 0 ? TYPE_ORDER.size() : index;
    }

    private String chineseNumber(int index) {
        String[] values = {"一", "二", "三", "四", "五", "六", "七", "八"};
        return index >= 1 && index <= values.length ? values[index - 1] : String.valueOf(index);
    }

    private float points(BigDecimal cm) {
        return (cm == null ? 0f : cm.floatValue()) * CM_TO_POINT;
    }

    private String fallback(String value) { return value == null || value.isBlank() ? "暂无" : value; }
    private String text(String value) { return value == null ? "" : value; }

    private String formalTitle(PaperDTO.PaperVO paper) {
        String subject = text(paper.getSubject()).trim();
        String category = text(paper.getCategory()).trim();
        return "《" + (subject.isBlank() ? text(paper.getName()) : subject) + "》"
                + (category.isBlank() ? "试题" : category) + "（A卷）";
    }

    private String compactTitle(PaperDTO.PaperVO paper) {
        String subject = text(paper.getSubject()).trim();
        String category = text(paper.getCategory()).trim();
        if (subject.isBlank()) subject = text(paper.getName()).trim();
        return subject + (category.isBlank() ? "试卷" : category);
    }

    private String safeFileName(String value) {
        String result = text(value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return result.isBlank() ? "试卷" : result;
    }

    public record ExportedPdf(String fileName, byte[] content) {}

    private static class PaperPageEvent extends PdfPageEventHelper {
        private final BaseFont font;
        private final PaperLayout layout;
        private PdfTemplate totalPages;

        private final boolean sealedA3;

        private PaperPageEvent(BaseFont font, PaperLayout layout, boolean sealedA3) {
            this.font = font;
            this.layout = layout;
            this.sealedA3 = sealedA3;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            if (sealedA3) {
                drawSealedA3Frame(canvas, writer, document);
                return;
            }
            int page = writer.getPageNumber();
            String prefix = "第 " + page + " 页 / 共 ";
            float size = 9f;
            float prefixWidth = font.getWidthPoint(prefix, size);
            float totalWidth = prefixWidth + 30f;
            float x = (document.getPageSize().getWidth() - totalWidth) / 2f;
            float y = Math.max(10f, document.bottomMargin() / 2f);
            canvas.beginText();
            canvas.setFontAndSize(font, size);
            canvas.setTextMatrix(x, y);
            canvas.showText(prefix);
            canvas.endText();
            canvas.addTemplate(totalPages, x + prefixWidth, y);

            if (Boolean.TRUE.equals(layout.getBindingLine())) drawBindingLine(canvas, document);
        }

        private void drawSealedA3Frame(PdfContentByte canvas, PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            float bottom = page.getBottom() + 18f;
            float top = page.getTop() - 18f;
            boolean firstSheet = writer.getPageNumber() == 1;

            if (firstSheet) {
                canvas.saveState();
                canvas.setColorFill(new Color(178, 178, 178));
                canvas.rectangle(page.getLeft() + 24f, bottom, 50f, top - bottom);
                canvas.fill();

                canvas.setColorStroke(new Color(90, 90, 90));
                canvas.setLineWidth(0.45f);
                canvas.setLineDash(1.5f, 3f);
                for (float y = bottom + 24f; y < top - 16f; y += 30f) {
                    canvas.moveTo(page.getLeft() + 7f, y);
                    canvas.lineTo(page.getLeft() + 17f, y);
                }
                canvas.stroke();

                canvas.setLineDash(0f);
                canvas.setColorStroke(new Color(80, 80, 80));
                canvas.setLineWidth(0.55f);
                canvas.moveTo(page.getLeft() + 17f, bottom);
                canvas.lineTo(page.getLeft() + 17f, top);
                canvas.moveTo(page.getLeft() + 80f, bottom);
                canvas.lineTo(page.getLeft() + 80f, top);
                canvas.stroke();
                canvas.restoreState();

                Font sealFont = new Font(font, 7.5f, Font.NORMAL, Color.WHITE);
                ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                        new Phrase("请 不 要 在 装 订 线 内 答 题", sealFont),
                        page.getLeft() + 49f, page.getHeight() / 2f, 90f);

                canvas.saveState();
                canvas.setColorStroke(new Color(90, 90, 90));
                canvas.setLineDash(2.5f, 2.5f);
                canvas.setLineWidth(0.5f);
                canvas.moveTo(page.getLeft() + 74f, bottom);
                canvas.lineTo(page.getLeft() + 74f, top);
                canvas.stroke();
                canvas.restoreState();

                Font candidateFont = new Font(font, 7.2f, Font.NORMAL, Color.DARK_GRAY);
                String[] candidateFields = {"学校：________", "班级：________", "姓名：________", "学号：________"};
                float fieldStep = (top - bottom - 70f) / (candidateFields.length - 1);
                for (int i = 0; i < candidateFields.length; i++) {
                    ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                            new Phrase(candidateFields[i], candidateFont),
                            page.getLeft() + 94f, top - 35f - i * fieldStep, 90f);
                }
            }

            canvas.saveState();
            float divider = (document.left() + document.right()) / 2f;
            canvas.setColorStroke(Color.BLACK);
            canvas.setLineWidth(0.8f);
            canvas.moveTo(divider, page.getBottom() + 27f);
            canvas.lineTo(divider, page.getTop() - 27f);
            canvas.stroke();
            canvas.restoreState();

            int firstLogicalPage = writer.getPageNumber() * 2 - 1;
            float contentWidth = document.right() - document.left();
            float gap = 18f;
            float columnWidth = (contentWidth - gap) / 2f;
            float footerY = page.getBottom() + 14f;
            drawLogicalFooter(canvas, "第 " + firstLogicalPage + " 页 共 ",
                    document.left() + columnWidth / 2f, footerY);
            drawLogicalFooter(canvas, "第 " + (firstLogicalPage + 1) + " 页 共 ",
                    document.left() + columnWidth + gap + columnWidth / 2f, footerY);
        }

        private void drawLogicalFooter(PdfContentByte canvas, String prefix, float centerX, float y) {
            float size = 7f;
            float prefixWidth = font.getWidthPoint(prefix, size);
            float totalWidth = prefixWidth + 25f;
            float x = centerX - totalWidth / 2f;
            canvas.beginText();
            canvas.setFontAndSize(font, size);
            canvas.setTextMatrix(x, y);
            canvas.showText(prefix);
            canvas.endText();
            canvas.addTemplate(totalPages, x + prefixWidth, y);
        }

        private void drawBindingLine(PdfContentByte canvas, Document document) {
            Rectangle page = document.getPageSize();
            boolean right = "right".equals(layout.getBindingPosition());
            float x = right ? page.getRight() - 18f : page.getLeft() + 18f;
            canvas.saveState();
            canvas.setLineDash(3f, 3f);
            canvas.setColorStroke(Color.DARK_GRAY);
            canvas.moveTo(x, page.getBottom() + 28f);
            canvas.lineTo(x, page.getTop() - 28f);
            canvas.stroke();
            canvas.restoreState();
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, new Phrase("装 订 线", new Font(font, 9f)),
                    right ? x - 7f : x + 7f, page.getHeight() / 2f, 90f);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(font, sealedA3 ? 7f : 9f);
            totalPages.setTextMatrix(0, 0);
            int physicalPages = writer.getPageNumber() - 1;
            totalPages.showText((sealedA3 ? physicalPages * 2 : physicalPages) + " 页");
            totalPages.endText();
        }
    }
}
