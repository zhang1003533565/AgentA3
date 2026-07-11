package com.example.appbackend.service;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColumns;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ExamPaperDocumentGenerator {

    private static final BigInteger COLUMN_SPACE = BigInteger.valueOf(425);
    private final ObjectMapper objectMapper;

    public ExamPaperDocumentGenerator() {
        this(new ObjectMapper());
    }

    ExamPaperDocumentGenerator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public byte[] generate(PaperVO paper, DownloadContent content) {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(content, "content");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configureLayout(document, paper);
            writeHeader(document, paper, content);
            int number = 1;
            for (QuestionSection section : groupQuestions(paper.getQuestions())) {
                writeSectionHeading(document, section.heading());
                for (QuestionSnapshotVO question : section.questions()) {
                    writeQuestion(document, question, number++);
                    if (content == DownloadContent.ANSWER) {
                        writeAnswer(document, question);
                    }
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成 DOCX 失败", exception);
        }
    }

    private void configureLayout(XWPFDocument document, PaperVO paper) {
        long[] dimensions = dimensions(paper.getPageSize());
        boolean landscape = paper.getOrientation() == Orientation.LANDSCAPE;
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        CTPageSz page = section.addNewPgSz();
        page.setW(BigInteger.valueOf(landscape ? dimensions[1] : dimensions[0]));
        page.setH(BigInteger.valueOf(landscape ? dimensions[0] : dimensions[1]));
        if (landscape) {
            page.setOrient(STPageOrientation.LANDSCAPE);
        }
        int count = Integer.valueOf(2).equals(paper.getColumnsCount()) ? 2 : 1;
        CTColumns columns = section.addNewCols();
        columns.setNum(BigInteger.valueOf(count));
        columns.setSpace(COLUMN_SPACE);
        columns.setSep(count == 2);
    }

    private long[] dimensions(PageSize pageSize) {
        return switch (pageSize == null ? PageSize.A4 : pageSize) {
            case A3 -> new long[]{16838, 23811};
            case A4 -> new long[]{11906, 16838};
            case B4 -> new long[]{14173, 20013};
        };
    }

    private void writeHeader(XWPFDocument document, PaperVO paper, DownloadContent content) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        titleRun.setText(safeFileText(paper.getTitle()));
        addCentered(document, paper.getSubtitle());
        addParagraph(document, paper.getHeaderInfo());
        List<String> facts = new ArrayList<>();
        if (paper.getDurationMinutes() != null) facts.add("考试时间：" + paper.getDurationMinutes() + " 分钟");
        if (paper.getTotalScore() != null) facts.add("总分：" + paper.getTotalScore().stripTrailingZeros().toPlainString() + " 分");
        if (content == DownloadContent.ANSWER) facts.add("参考答案");
        addParagraph(document, String.join("    ", facts));
        if (paper.getPrecautions() != null && !paper.getPrecautions().isBlank()) {
            addParagraph(document, "注意事项：" + safeFileText(paper.getPrecautions()));
        }
    }

    private List<QuestionSection> groupQuestions(List<QuestionSnapshotVO> questions) {
        List<QuestionSection> sections = new ArrayList<>();
        if (questions == null) return sections;
        List<QuestionSnapshotVO> ordered = questions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(QuestionSnapshotVO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (QuestionSnapshotVO question : ordered) {
            String heading = typeName(question.getType());
            if (sections.isEmpty() || !sections.getLast().heading().equals(heading)) {
                sections.add(new QuestionSection(heading, new ArrayList<>()));
            }
            sections.getLast().questions().add(question);
        }
        return sections;
    }

    private void writeSectionHeading(XWPFDocument document, String heading) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(13);
        run.setText(heading);
    }

    private void writeQuestion(XWPFDocument document, QuestionSnapshotVO question, int number) {
        String score = question.getScore() == null ? "" : "（" + question.getScore().stripTrailingZeros().toPlainString() + " 分）";
        addParagraph(document, number + ". " + safeFileText(question.getStem()) + score);
        JsonNode body = readJson(question.getBodyJson());
        if (body == null) {
            addParagraph(document, safeFileText(question.getBodyJson()));
            return;
        }
        boolean rendered = writeOptions(document, body);
        rendered = writeReadableBody(document, body) || rendered;
        if (!rendered) addParagraph(document, pretty(body));
    }

    private boolean writeOptions(XWPFDocument document, JsonNode body) {
        JsonNode options = body.path("options");
        if (!options.isArray()) return false;
        boolean rendered = false;
        for (JsonNode option : options) {
            String key = option.path("key").asText("");
            String value = option.path("text").asText("");
            if (!key.isBlank() || !value.isBlank()) {
                addParagraph(document, key + ". " + value);
                rendered = true;
            }
        }
        return rendered;
    }

    private boolean writeReadableBody(XWPFDocument document, JsonNode body) {
        boolean rendered = false;
        for (String key : List.of("statement", "text", "material", "task", "description", "inputFormat", "outputFormat")) {
            JsonNode value = body.get(key);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                addParagraph(document, value.asText());
                rendered = true;
            }
        }
        for (String key : List.of("requirements", "constraints", "items", "leftItems", "rightItems", "subQuestions", "examples")) {
            JsonNode value = body.get(key);
            if (value != null && !value.isEmpty()) {
                addParagraph(document, pretty(value));
                rendered = true;
            }
        }
        return rendered;
    }

    private void writeAnswer(XWPFDocument document, QuestionSnapshotVO question) {
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
        addParagraph(document, "标准答案：" + text);
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            addParagraph(document, "解析：" + safeFileText(question.getAnalysis()));
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

    private void addCentered(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.createRun().setText(safeFileText(text));
    }

    private void addParagraph(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) return;
        document.createParagraph().createRun().setText(safeFileText(text));
    }

    private record QuestionSection(String heading, List<QuestionSnapshotVO> questions) {
    }
}
