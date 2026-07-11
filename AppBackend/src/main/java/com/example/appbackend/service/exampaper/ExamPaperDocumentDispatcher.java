package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperRenderMode;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.service.ExamPaperDocumentGenerator;

import java.util.Objects;

/** Selects the source-faithful template renderer or the pre-existing simple renderer. */
public final class ExamPaperDocumentDispatcher {
    private final ExamPaperDocumentGenerator simpleGenerator;
    private final SourcePaperTemplateEngine templateEngine;

    public ExamPaperDocumentDispatcher() {
        this(new ExamPaperDocumentGenerator(), new SourcePaperTemplateEngine());
    }

    public ExamPaperDocumentDispatcher(ExamPaperDocumentGenerator simpleGenerator,
                                       SourcePaperTemplateEngine templateEngine) {
        this.simpleGenerator = Objects.requireNonNull(simpleGenerator);
        this.templateEngine = Objects.requireNonNull(templateEngine);
    }

    public byte[] generate(PaperVO paper, DownloadContent content, PaperLayoutConfig layout) {
        Objects.requireNonNull(layout, "layout");
        if (layout.getRenderMode() == PaperRenderMode.SIMPLE) {
            return simpleGenerator.generate(paper, content);
        }
        return templateEngine.generate(paper, content, layout);
    }
}
