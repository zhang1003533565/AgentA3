package com.example.appbackend.service;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.PaperLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperWordExportServiceTest {
    @Test
    void exportsEditableStudentAndAnswerDocumentsFromPaperAndLayoutServices() throws Exception {
        PaperService paperService = mock(PaperService.class);
        PaperLayoutService layoutService = mock(PaperLayoutService.class);
        PaperDTO.PaperVO paper = paper();
        PaperLayout layout = layout();
        when(paperService.getPaper(29L, 7L)).thenReturn(paper);
        when(layoutService.get(29L, 7L)).thenReturn(layout);

        PaperWordExportService service = new PaperWordExportService(paperService, layoutService, new ObjectMapper());
        PaperWordExportService.ExportedWord student = service.export(29L, 7L, false);
        PaperWordExportService.ExportedWord answers = service.export(29L, 7L, true);

        assertEquals("Python程序设计期末试卷.docx", student.fileName());
        assertEquals("Python程序设计期末试卷-答案版.docx", answers.fileName());
        assertTrue(student.content().length > 1_000);
        assertTrue(answers.content().length > student.content().length);
        verify(paperService, org.mockito.Mockito.times(2)).getPaper(29L, 7L);
        verify(layoutService, org.mockito.Mockito.times(2)).get(29L, 7L);

        assertDocument(student.content(), false);
        assertDocument(answers.content(), true);
    }

    private void assertDocument(byte[] content, boolean answers) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            String text = document.getParagraphs().stream().map(XWPFParagraph::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("Python程序设计期末试卷"));
            assertTrue(text.contains("总分：12分"));
            assertTrue(text.contains("一、单项选择题（共1题，共7分）"));
            assertTrue(text.contains("2. Python中哪个关键字用于定义函数？（7分）"));
            assertTrue(text.contains("A. def"));
            assertTrue(text.contains("二、判断题（共1题，共5分）"));
            assertTrue(text.contains("1. Python是一种解释型语言。（5分）"));
            assertFalse(text.contains("学号 ____________"));
            assertEquals(answers, text.contains("【答案】def"));
            assertEquals(answers, text.contains("【解析】使用def关键字定义函数。"));

            CTSectPr section = document.getDocument().getBody().getSectPr();
            assertEquals(STPageOrientation.LANDSCAPE, section.getPgSz().getOrient());
            assertEquals(23811, integer(section.getPgSz().getW()));
            assertEquals(16838, integer(section.getPgSz().getH()));
            assertEquals(2, section.getCols().getNum().intValue());
            assertTrue(document.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getCTP().isSetPPr()
                            && paragraph.getCTP().getPPr().isSetSectPr()
                            && paragraph.getCTP().getPPr().getSectPr().getCols().getNum().intValue() == 1));
        }
    }

    private PaperDTO.PaperVO paper() {
        PaperDTO.PaperVO paper = new PaperDTO.PaperVO();
        paper.setId(29L);
        paper.setName("Python程序设计期末试卷");
        paper.setSubject("Python程序设计");
        paper.setDuration(90);
        paper.setTotalScore(12);

        PaperDTO.PaperQuestionVO judgment = question(1L, 1, 5, "判断题",
                "Python是一种解释型语言。", null, "正确", "Python代码由解释器执行。");
        PaperDTO.PaperQuestionVO choice = question(2L, 2, 7, "单选题",
                "Python中哪个关键字用于定义函数？", "[\"def\",\"class\",\"return\",\"import\"]",
                "def", "使用def关键字定义函数。");
        paper.setQuestions(List.of(judgment, choice));
        return paper;
    }

    private PaperDTO.PaperQuestionVO question(Long id, int order, int score, String type,
                                               String content, String options, String answer, String analysis) {
        PaperDTO.QuestionVO question = new PaperDTO.QuestionVO();
        question.setId(id);
        question.setQuestionType(type);
        question.setContent(content);
        question.setOptions(options);
        question.setAnswer(answer);
        question.setAnalysis(analysis);

        PaperDTO.PaperQuestionVO item = new PaperDTO.PaperQuestionVO();
        item.setQuestionId(id);
        item.setQuestionOrder(order);
        item.setScore(score);
        item.setQuestion(question);
        return item;
    }

    private PaperLayout layout() {
        PaperLayout layout = new PaperLayout();
        layout.setPaperSize("A3");
        layout.setOrientation("landscape");
        layout.setColumnsCount(2);
        layout.setColumnGap(new BigDecimal("0.75"));
        layout.setMarginTop(new BigDecimal("2.00"));
        layout.setMarginBottom(new BigDecimal("2.00"));
        layout.setMarginLeft(new BigDecimal("2.20"));
        layout.setMarginRight(new BigDecimal("2.20"));
        layout.setShowSchool(true);
        layout.setShowGrade(false);
        layout.setShowClass(true);
        layout.setShowName(true);
        layout.setShowStudentNo(false);
        layout.setTitleFontSize(26);
        layout.setSubtitleFontSize(18);
        layout.setBodyFontSize(11);
        return layout;
    }

    private int integer(Object value) {
        return new BigInteger(String.valueOf(value)).intValue();
    }
}
