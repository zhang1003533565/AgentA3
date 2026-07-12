package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.QuestionSnapshotVO;
import com.example.appbackend.service.ExamPaperDocumentGenerator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates deterministic visual-QA artifacts only when exam.visual.output is supplied. */
class SourcePaperVisualFixtureTest {

    @Test
    void writesSourceFaithfulVisualMatrix() throws Exception {
        String configured = System.getProperty("exam.visual.output");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "visual fixture generation is opt-in");
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);

        PaperVO paper = representativePaper();
        SourcePaperTemplateEngine template = new SourcePaperTemplateEngine();
        ExamPaperDocumentDispatcher dispatcher = new ExamPaperDocumentDispatcher(
                new ExamPaperDocumentGenerator(), template);

        Map<String, PaperLayoutConfig> cases = matrix();
        List<String> manifest = new ArrayList<>();
        for (Map.Entry<String, PaperLayoutConfig> item : cases.entrySet()) {
            byte[] bytes = template.generate(paper, DownloadContent.PAPER, item.getValue());
            SourcePaperPackageVerifier.verify(bytes);
            Files.write(output.resolve(item.getKey() + "-paper.docx"), bytes);
            manifest.add(manifest(item.getKey() + "-paper.docx", item.getValue(), false, 1, 4));
        }
        PaperLayoutConfig sourceDefault = cases.get("template-a3-landscape-binding-2col");
        byte[] answer = template.generate(paper, DownloadContent.ANSWER, sourceDefault);
        SourcePaperPackageVerifier.verify(answer);
        Files.write(output.resolve("template-a3-landscape-binding-2col-answer.docx"), answer);
        manifest.add(manifest("template-a3-landscape-binding-2col-answer.docx", sourceDefault, true, 1, 4));

        String soffice = System.getProperty("exam.visual.soffice");
        String previewRoot = System.getProperty("exam.visual.previewRoot");
        if (soffice != null && previewRoot != null) {
            var converted = new LibreOfficePreviewConverter(soffice, Duration.ofMinutes(2), Path.of(previewRoot))
                    .convert(Files.readAllBytes(output.resolve("template-a3-landscape-binding-2col-paper.docx")),
                            Path.of(previewRoot).resolve("visual/default"));
            Files.write(output.resolve("template-a3-landscape-binding-2col-paper.preview.pdf"), converted.bytes());
        }

        PaperLayoutConfig simple = new PaperLayoutConfig();
        simple.setRenderMode(PaperRenderMode.SIMPLE);
        Files.write(output.resolve("simple-a4-portrait-paper.docx"),
                dispatcher.generate(paper, DownloadContent.PAPER, simple));
        Files.writeString(output.resolve("manifest.json"), "[\n" + String.join(",\n", manifest) + "\n]\n");
    }

    private static String manifest(String file, PaperLayoutConfig layout, boolean answer,
                                   int scoreTables, int graderTables) {
        SourcePaperLayoutResolver.ResolvedPageLayout page = new SourcePaperLayoutResolver().resolve(layout);
        return "  {\"file\":\"" + file + "\",\"pageSize\":\"" + layout.getPageSize()
                + "\",\"orientation\":\"" + layout.getOrientation() + "\",\"width\":" + page.pageWidth()
                + ",\"height\":" + page.pageHeight() + ",\"top\":" + page.marginTop() + ",\"right\":" + page.marginRight()
                + ",\"bottom\":" + page.marginBottom() + ",\"left\":" + page.marginLeft() + ",\"columns\":"
                + layout.getColumnsCount() + ",\"space\":" + layout.getColumnSpace() + ",\"binding\":"
                + layout.getHasBindingLine() + ",\"docGrid\":" + page.documentGridLinePitch()
                + ",\"answer\":" + answer + ",\"scoreTables\":"
                + scoreTables + ",\"graderTables\":" + graderTables + "}";
    }

    private static Map<String, PaperLayoutConfig> matrix() {
        Map<String, PaperLayoutConfig> cases = new LinkedHashMap<>();
        cases.put("template-a3-landscape-binding-2col", layout(PageSize.A3, Orientation.LANDSCAPE,
                MarginPreset.BINDING, 2, true));
        cases.put("template-a3-portrait-normal-1col", layout(PageSize.A3, Orientation.PORTRAIT,
                MarginPreset.NORMAL, 1, true));
        cases.put("template-a4-landscape-narrow-2col", layout(PageSize.A4, Orientation.LANDSCAPE,
                MarginPreset.NARROW, 2, false));
        cases.put("template-a4-portrait-wide-1col", layout(PageSize.A4, Orientation.PORTRAIT,
                MarginPreset.WIDE, 1, false));
        cases.put("template-b4-landscape-binding-2col", layout(PageSize.B4, Orientation.LANDSCAPE,
                MarginPreset.BINDING, 2, true));
        cases.put("template-b4-portrait-custom-1col", layout(PageSize.B4, Orientation.PORTRAIT,
                MarginPreset.CUSTOM, 1, true));
        PaperLayoutConfig font = layout(PageSize.A4, Orientation.PORTRAIT, MarginPreset.NORMAL, 1, false);
        font.setTitleFontSize(62);
        font.setSubtitleFontSize(30);
        font.setBodyFontSize(24);
        font.setColumnSpace(720);
        cases.put("template-a4-portrait-font-adjusted", font);
        return cases;
    }

    private static PaperLayoutConfig layout(PageSize size, Orientation orientation,
                                             MarginPreset margins, int columns, boolean binding) {
        PaperLayoutConfig layout = new PaperLayoutConfig();
        layout.setPageSize(size);
        layout.setOrientation(orientation);
        layout.setMarginPreset(margins);
        layout.setColumnsCount(columns);
        layout.setHasBindingLine(binding);
        layout.setHeaderInfo("煤矿___________  部门___________  岗位___________  姓名___________");
        if (margins == MarginPreset.CUSTOM) {
            layout.setCustomMarginTop(900);
            layout.setCustomMarginRight(1080);
            layout.setCustomMarginBottom(1260);
            layout.setCustomMarginLeft(1440);
        }
        return layout;
    }

    private static PaperVO representativePaper() {
        PaperVO paper = new PaperVO();
        paper.setTitle("嘿哈煤业监测监控维护工");
        paper.setSubtitle("(全卷满分: 100分，考试时间: 60分钟)");
        paper.setDurationMinutes(60);
        paper.setPrecautions("考试期间请遵守考场纪律，认真审题，规范作答。");
        paper.setHeaderInfo("煤矿___________  部门___________  岗位___________  姓名___________");
        paper.setTotalScore(new BigDecimal("100"));
        List<QuestionSnapshotVO> questions = new ArrayList<>();
        int sort = 1;
        for (int index = 1; index <= 10; index++) {
            questions.add(choice(sort++, 1, "single_choice", new BigDecimal("2"),
                    "建设项目的安全设施、职业病危害防护设施与主体工程的要求包括？",
                    index % 3 == 0));
        }
        for (int index = 1; index <= 10; index++) {
            questions.add(choice(sort++, 2, "multiple_choice", new BigDecimal("3"),
                    "安全监控设备故障处置期间应当采取的措施包括哪些？",
                    index % 2 == 0));
        }
        for (int index = 1; index <= 5; index++) {
            QuestionSnapshotVO question = base(sort++, 3, "judgment", new BigDecimal("2"),
                    "井下作业人员应按规定检查设备运行状态。（ ）");
            question.setAnswerJson("{\"correct\":true}");
            questions.add(question);
        }
        for (int index = 1; index <= 2; index++) {
            QuestionSnapshotVO question = base(sort++, 4, "essay", new BigDecimal("20"),
                    "简述安全监测监控系统日常维护的主要步骤及异常处置要求。");
            question.setAnswerJson("{\"referenceAnswer\":\"按制度巡检、记录参数、发现异常立即报告并采取安全措施。\"}");
            question.setAnalysis("答案应包含巡检、记录、报告和安全处置四个要点。");
            questions.add(question);
        }
        paper.setQuestions(questions);
        paper.setQuestionCount(questions.size());
        return paper;
    }

    private static QuestionSnapshotVO choice(int sort, int section, String type, BigDecimal score,
                                             String stem, boolean longOptions) {
        QuestionSnapshotVO question = base(sort, section, type, score, stem);
        question.setBodyJson(longOptions
                ? "{\"options\":[{\"key\":\"A\",\"text\":\"及时检查并按照规定形成完整处置记录\"},{\"key\":\"B\",\"text\":\"停止相关作业并报告现场负责人\"},{\"key\":\"C\",\"text\":\"确认设备恢复正常后再投入使用\"},{\"key\":\"D\",\"text\":\"无需处理等待自动恢复\"}]}"
                : "{\"options\":[{\"key\":\"A\",\"text\":\"及时处理\"},{\"key\":\"B\",\"text\":\"规范记录\"},{\"key\":\"C\",\"text\":\"立即报告\"},{\"key\":\"D\",\"text\":\"无需处置\"}]}");
        question.setAnswerJson(type.equals("multiple_choice")
                ? "{\"correctOptions\":[\"A\",\"B\",\"C\"]}"
                : "{\"correctOption\":\"A\"}");
        question.setAnalysis("依据安全生产操作规程作答。");
        return question;
    }

    private static QuestionSnapshotVO base(int sort, int section, String type, BigDecimal score, String stem) {
        QuestionSnapshotVO question = new QuestionSnapshotVO();
        question.setSortOrder(sort);
        question.setSectionOrder(section);
        question.setType(type);
        question.setScore(score);
        question.setStem(stem);
        return question;
    }
}
