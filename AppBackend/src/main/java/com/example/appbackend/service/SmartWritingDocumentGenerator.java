package com.example.appbackend.service;

import com.example.appbackend.dto.AiWriteDTO;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

@Service
public class SmartWritingDocumentGenerator {

    private static final int TITLE_FONT_SIZE = 36;
    private static final int META_FONT_SIZE = 20;
    private static final int BODY_FONT_SIZE = 24;

    public byte[] generate(AiWriteDTO.ExportRequest request) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            writeTitle(document, request.getTitle());
            writeMetadata(document, request);
            writeContent(document, request.getContent());

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成 Word 文档失败", exception);
        }
    }

    private void writeTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(200);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        setFontSize(run, TITLE_FONT_SIZE);
        run.setText(safeText(title));
    }

    private void writeMetadata(XWPFDocument document, AiWriteDTO.ExportRequest request) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingAfter(300);

        StringBuilder meta = new StringBuilder();
        if (request.getSceneLabel() != null && !request.getSceneLabel().isBlank()) {
            meta.append("类型：").append(request.getSceneLabel());
        }
        if (request.getGeneratedAt() != null && !request.getGeneratedAt().isBlank()) {
            if (!meta.isEmpty()) meta.append("    ");
            meta.append("生成时间：").append(request.getGeneratedAt());
        }
        if (request.getModel() != null && !request.getModel().isBlank()) {
            if (!meta.isEmpty()) meta.append("    ");
            meta.append("模型：").append(request.getModel());
        }

        if (!meta.isEmpty()) {
            XWPFRun run = paragraph.createRun();
            setFontSize(run, META_FONT_SIZE);
            run.setColor("666666");
            run.setText(meta.toString());
        }
    }

    private void writeContent(XWPFDocument document, String content) {
        if (content == null || content.isBlank()) return;

        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(100);
            paragraph.setSpacingAfter(100);
            XWPFRun run = paragraph.createRun();
            setFontSize(run, BODY_FONT_SIZE);
            run.setText(safeText(line));
        }
    }

    private void setFontSize(XWPFRun run, int halfPoints) {
        var properties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        properties.addNewSz().setVal(BigInteger.valueOf(halfPoints));
        properties.addNewSzCs().setVal(BigInteger.valueOf(halfPoints));
    }

    private String safeText(String text) {
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
}
