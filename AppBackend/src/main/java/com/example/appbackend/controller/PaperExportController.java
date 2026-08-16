package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PaperExportService;
import com.example.appbackend.service.PaperWordExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/papers/{paperId}/export")
public class PaperExportController {
    private static final MediaType WORD_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final PaperExportService service;
    private final PaperWordExportService wordService;

    public PaperExportController(PaperExportService service, PaperWordExportService wordService) {
        this.service = service;
        this.wordService = wordService;
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long paperId,
                                      @RequestParam(defaultValue = "false") boolean answers,
                                      HttpServletRequest request) {
        PaperExportService.ExportedPdf exported = service.export(paperId, user(request), answers);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(exported.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }

    @GetMapping(value = "/word", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> word(@PathVariable Long paperId,
                                       @RequestParam(defaultValue = "false") boolean answers,
                                       HttpServletRequest request) {
        PaperWordExportService.ExportedWord exported = wordService.export(paperId, user(request), answers);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(WORD_MEDIA_TYPE)
                .contentLength(exported.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }

    private Long user(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        if (id == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) id;
    }
}
