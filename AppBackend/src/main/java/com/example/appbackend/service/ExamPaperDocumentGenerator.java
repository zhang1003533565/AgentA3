package com.example.appbackend.service;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.service.exampaper.SourcePaperLayoutResolver;
import com.example.appbackend.service.exampaper.SourcePaperLayoutResolver.ResolvedPageLayout;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColumns;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ExamPaperDocumentGenerator {

    private final ObjectMapper objectMapper;
    private final SourcePaperLayoutResolver layoutResolver;

    public ExamPaperDocumentGenerator() {
        this(new ObjectMapper(), new SourcePaperLayoutResolver());
    }

    ExamPaperDocumentGenerator(ObjectMapper objectMapper) {
        this(objectMapper, new SourcePaperLayoutResolver());
    }

    ExamPaperDocumentGenerator(ObjectMapper objectMapper, SourcePaperLayoutResolver layoutResolver) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.layoutResolver = Objects.requireNonNull(layoutResolver);
    }

    public byte[] generate(PaperVO paper, DownloadContent content) {
        PaperLayoutConfig legacy = new PaperLayoutConfig();
        if (paper != null) {
            if (paper.getPageSize() != null) legacy.setPageSize(paper.getPageSize());
            if (paper.getOrientation() != null) legacy.setOrientation(paper.getOrientation());
            if (paper.getColumnsCount() != null) legacy.setColumnsCount(paper.getColumnsCount());
            if (paper.getHeaderInfo() != null) legacy.setHeaderInfo(paper.getHeaderInfo());
        }
        return generate(paper, content, legacy);
    }

    public byte[] generate(PaperVO paper, DownloadContent content, PaperLayoutConfig layout) {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(layout, "layout");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ResolvedPageLayout resolved = layoutResolver.resolve(layout);
            configureLayout(document, resolved);
            writeBindingHeader(document, layout);
            writeHeader(document, paper, content, layout);
            int number = 1;
            for (QuestionSection section : groupQuestions(paper.getQuestions())) {
                writeSectionHeading(document, section, layout.getBodyFontSize());
                for (QuestionSnapshotVO question : section.questions()) {
                    writeQuestion(document, question, number++, layout.getBodyFontSize());
                    if (content == DownloadContent.ANSWER) {
                        writeAnswer(document, question, layout.getBodyFontSize());
                    }
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成 DOCX 失败", exception);
        }
    }

    private void configureLayout(XWPFDocument document, ResolvedPageLayout layout) {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        CTPageSz page = section.addNewPgSz();
        page.setW(BigInteger.valueOf(layout.pageWidth()));
        page.setH(BigInteger.valueOf(layout.pageHeight()));
        page.setOrient(layout.orientation().name().equals("LANDSCAPE")
                ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);
        CTPageMar margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(layout.marginTop()));
        margins.setRight(BigInteger.valueOf(layout.marginRight()));
        margins.setBottom(BigInteger.valueOf(layout.marginBottom()));
        margins.setLeft(BigInteger.valueOf(layout.marginLeft()));
        margins.setHeader(BigInteger.valueOf(layout.header()));
        margins.setFooter(BigInteger.valueOf(layout.footer()));
        margins.setGutter(BigInteger.valueOf(layout.gutter()));
        CTColumns columns = section.addNewCols();
        columns.setNum(BigInteger.valueOf(layout.columnsCount()));
        columns.setSpace(BigInteger.valueOf(layout.columnSpace()));
        columns.setSep(layout.columnSeparator());
        CTDocGrid grid = section.addNewDocGrid();
        grid.setLinePitch(BigInteger.valueOf(layout.documentGridLinePitch()));
    }

    private void writeBindingHeader(XWPFDocument document, PaperLayoutConfig layout) {
        if (!Boolean.TRUE.equals(layout.getHasBindingLine())) return;
        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = header.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        setHalfPointFontSize(run, layout.getBodyFontSize());
        run.setText("装订线    " + safeFileText(layout.getHeaderInfo()));
    }

    private void writeHeader(XWPFDocument document, PaperVO paper, DownloadContent content,
                             PaperLayoutConfig layout) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        setHalfPointFontSize(titleRun, layout.getTitleFontSize());
        titleRun.setText(safeFileText(paper.getTitle()));
        addCentered(document, paper.getSubtitle(), layout.getSubtitleFontSize());
        List<String> facts = new ArrayList<>();
        if (paper.getDurationMinutes() != null) facts.add("考试时间：" + paper.getDurationMinutes() + " 分钟");
        if (paper.getTotalScore() != null) facts.add("总分：" + paper.getTotalScore().stripTrailingZeros().toPlainString() + " 分");
        if (content == DownloadContent.ANSWER) facts.add("参考答案");
        addParagraph(document, String.join("    ", facts), layout.getBodyFontSize());
        if (paper.getPrecautions() != null && !paper.getPrecautions().isBlank()) {
            addParagraph(document, "注意事项：" + safeFileText(paper.getPrecautions()), layout.getBodyFontSize());
        }
    }

    private List<QuestionSection> groupQuestions(List<QuestionSnapshotVO> questions) {
        if (questions == null) return List.of();
        List<QuestionSnapshotVO> ordered = questions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(QuestionSnapshotVO::getSectionOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(QuestionSnapshotVO::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<SectionKey, List<QuestionSnapshotVO>> grouped = new LinkedHashMap<>();
        for (QuestionSnapshotVO question : ordered) {
            grouped.computeIfAbsent(new SectionKey(question.getSectionOrder(), question.getType()),
                    ignored -> new ArrayList<>()).add(question);
        }
        return grouped.entrySet().stream()
                .map(entry -> new QuestionSection(typeName(entry.getKey().type()), entry.getValue()))
                .toList();
    }

    private void writeSectionHeading(XWPFDocument document, QuestionSection section, int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        setHalfPointFontSize(run, fontSize);
        String score = section.questions().stream()
                .map(QuestionSnapshotVO::getScore)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .stripTrailingZeros()
                .toPlainString();
        run.setText(section.heading() + "（共" + section.questions().size() + "题，" + score + "分）");
    }

    private void writeQuestion(XWPFDocument document, QuestionSnapshotVO question, int number, int fontSize) {
        String score = question.getScore() == null ? "" : "（" + question.getScore().stripTrailingZeros().toPlainString() + " 分）";
        addParagraph(document, number + ". " + safeFileText(question.getStem()) + score, fontSize);
        JsonNode body = readJson(question.getBodyJson());
        if (body == null) {
            addParagraph(document, safeFileText(question.getBodyJson()), fontSize);
            return;
        }
        if (body.isNull() || body.isEmpty()) return;
        boolean rendered = writeOptions(document, body, fontSize);
        rendered = writeReadableBody(document, body, fontSize) || rendered;
        if (!rendered) addParagraph(document, pretty(body), fontSize);
    }

    private boolean writeOptions(XWPFDocument document, JsonNode body, int fontSize) {
        JsonNode options = body.path("options");
        if (!options.isArray()) return false;
        boolean rendered = false;
        for (JsonNode option : options) {
            String key = option.path("key").asText("");
            String value = option.path("text").asText("");
            if (!key.isBlank() || !value.isBlank()) {
                addParagraph(document, key + ". " + value, fontSize);
                rendered = true;
            }
        }
        return rendered;
    }

    private boolean writeReadableBody(XWPFDocument document, JsonNode body, int fontSize) {
        boolean rendered = false;
        for (String key : List.of("statement", "text", "material", "task", "description", "inputFormat", "outputFormat")) {
            JsonNode value = body.get(key);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                addParagraph(document, value.asText(), fontSize);
                rendered = true;
            }
        }
        for (String key : List.of("requirements", "constraints", "items", "leftItems", "rightItems", "subQuestions", "examples")) {
            JsonNode value = body.get(key);
            if (value != null && !value.isEmpty()) {
                addParagraph(document, pretty(value), fontSize);
                rendered = true;
            }
        }
        return rendered;
    }

    private void writeAnswer(XWPFDocument document, QuestionSnapshotVO question, int fontSize) {
        JsonNode answer = readJson(question.getAnswerJson());
        String text;
        if (answer == null) {
            text = safeFileText(question.getAnswerJson());
        } else if (answer.has("correctOption")) {
            text = answer.path("correctOption").asText();
        } else if (answer.has("correctOptions")) {
            List<String> values = new ArrayList<>();
            answer.path("correctOptions").forEach(value -> values.add(value.asText()));
            text = String.join("、", values);
        } else if (answer.has("correct") && answer.path("correct").isBoolean()) {
            text = answer.path("correct").asBoolean() ? "正确" : "错误";
        } else if (answer.has("referenceAnswer")) {
            text = answer.path("referenceAnswer").asText();
        } else if (answer.has("finalAnswer")) {
            text = answer.path("finalAnswer").asText();
        } else if (answer.has("conclusion")) {
            text = answer.path("conclusion").asText();
        } else if (answer.has("expectedResult")) {
            text = answer.path("expectedResult").asText();
        } else {
            text = pretty(answer);
        }
        addParagraph(document, "标准答案：" + text, fontSize);
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            addParagraph(document, "解析：" + safeFileText(question.getAnalysis()), fontSize);
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String safeFileText(String text) {
        if (text == null) return "";
        StringBuilder safe = new StringBuilder(text.length());
        text.codePoints().filter(this::isXmlCharacter).forEach(safe::appendCodePoint);
        return safe.toString();
    }

    private boolean isXmlCharacter(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }

    private String pretty(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return node.toString();
        }
    }

    private String typeName(String type) {
        if (type == null || type.isBlank()) return "其他题型";
        return switch (type.toLowerCase()) {
            case "single_choice" -> "单项选择题";
            case "multiple_choice" -> "多项选择题";
            case "true_false" -> "判断题";
            case "fill_blank" -> "填空题";
            case "short_answer" -> "简答题";
            case "essay" -> "论述题";
            case "material_analysis" -> "材料分析题";
            case "calculation" -> "计算题";
            case "proof" -> "证明题";
            case "programming" -> "编程题";
            case "operation" -> "操作题";
            case "matching" -> "匹配题";
            case "ordering" -> "排序题";
            case "cloze" -> "完形填空题";
            default -> type;
        };
    }

    private void addCentered(XWPFDocument document, String text, int fontSize) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        setHalfPointFontSize(run, fontSize);
        run.setText(safeFileText(text));
    }

    private void addParagraph(XWPFDocument document, String text, int fontSize) {
        if (text == null || text.isBlank()) return;
        XWPFRun run = document.createParagraph().createRun();
        setHalfPointFontSize(run, fontSize);
        run.setText(safeFileText(text));
    }

    private void setHalfPointFontSize(XWPFRun run, int halfPoints) {
        var properties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        properties.addNewSz().setVal(BigInteger.valueOf(halfPoints));
        properties.addNewSzCs().setVal(BigInteger.valueOf(halfPoints));
    }

    private record QuestionSection(String heading, List<QuestionSnapshotVO> questions) {
    }

    private record SectionKey(Integer order, String type) {
    }
}
