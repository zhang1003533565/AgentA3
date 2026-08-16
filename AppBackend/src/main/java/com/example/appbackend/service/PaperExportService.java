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

        float marginTop = points(layout.getMarginTop());
        float marginBottom = points(layout.getMarginBottom());
        float marginLeft = points(layout.getMarginLeft());
        float marginRight = points(layout.getMarginRight());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(pageSize, marginLeft, marginRight, marginTop, marginBottom);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            BaseFont baseFont = chineseFont();
            writer.setPageEvent(new PaperPageEvent(baseFont, layout));
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

            Paragraph title = new Paragraph(paper.getName(), titleFont);
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

            float top = writer.getVerticalPosition(true);
            ColumnText columns = new ColumnText(writer.getDirectContent());
            addQuestionElements(columns, paper, answers, bodyFont, bodyBold, answerFont);
            flowColumns(columns, writer, document, layout, top, marginBottom);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            if (document.isOpen()) document.close();
            throw new IllegalStateException("PDF生成失败：" + exception.getMessage(), exception);
        }
    }

    private void addQuestionElements(ColumnText columns, PaperDTO.PaperVO paper, boolean answers,
                                     Font bodyFont, Font bodyBold, Font answerFont) {
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
            int score = group.getValue().stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).sum();
            String heading = chineseNumber(groupIndex + 1) + "、" + TYPE_TITLES.getOrDefault(group.getKey(), group.getKey())
                    + "（共" + group.getValue().size() + "题，共" + score + "分）";
            Paragraph groupTitle = new Paragraph(heading, bodyBold);
            groupTitle.setSpacingBefore(7f);
            groupTitle.setSpacingAfter(6f);
            groupTitle.setKeepTogether(true);
            columns.addElement(groupTitle);

            for (PaperDTO.PaperQuestionVO item : group.getValue()) {
                PaperDTO.QuestionVO question = item.getQuestion();
                if (question == null) continue;
                Paragraph content = new Paragraph(item.getQuestionOrder() + ". " + text(question.getContent())
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
                    gap.setSpacingAfter(5f);
                    columns.addElement(gap);
                }
            }
        }
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
        List<String> fields = new ArrayList<>();
        if (Boolean.TRUE.equals(layout.getShowSchool())) fields.add("学校 ____________");
        if (Boolean.TRUE.equals(layout.getShowGrade())) fields.add("年级 ____________");
        if (Boolean.TRUE.equals(layout.getShowClass())) fields.add("班级 ____________");
        if (Boolean.TRUE.equals(layout.getShowName())) fields.add("姓名 ____________");
        if (Boolean.TRUE.equals(layout.getShowStudentNo())) fields.add("学号 ____________");
        return fields;
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

    private String safeFileName(String value) {
        String result = text(value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return result.isBlank() ? "试卷" : result;
    }

    public record ExportedPdf(String fileName, byte[] content) {}

    private static class PaperPageEvent extends PdfPageEventHelper {
        private final BaseFont font;
        private final PaperLayout layout;
        private PdfTemplate totalPages;

        private PaperPageEvent(BaseFont font, PaperLayout layout) {
            this.font = font;
            this.layout = layout;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
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
            totalPages.setFontAndSize(font, 9f);
            totalPages.setTextMatrix(0, 0);
            totalPages.showText((writer.getPageNumber() - 1) + " 页");
            totalPages.endText();
        }
    }
}
