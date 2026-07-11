package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamPaperDTO.*;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.exampaper.ExamPaperDocumentDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamPaperPreviewServiceImplTest {
    @TempDir Path root;

    @Test
    void createsCreatorOwnedThirtyMinutePreviewWithHashesWithoutPersistingHistory() throws Exception {
        ExamQuestionRepository questions = mock(ExamQuestionRepository.class);
        ExamPaperDocumentDispatcher dispatcher = mock(ExamPaperDocumentDispatcher.class);
        when(questions.findAllById(List.of(3L))).thenReturn(List.of(question()));
        when(dispatcher.generate(any(), eq(DownloadContent.PAPER), any())).thenReturn(new byte[]{1});
        Instant now = Instant.parse("2026-07-11T12:00:00Z");
        var service = service(questions, dispatcher, Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC));

        var preview = service.createPreview(request(), 8L);
        assertDoesNotThrow(() -> java.util.UUID.fromString(preview.getToken()));
        assertEquals("/api/exam/papers/preview/" + preview.getToken(), preview.getPdfUrl());
        assertEquals(now.plus(Duration.ofMinutes(30)), preview.getExpiresAt());
        assertEquals(64, preview.getConfigurationHash().length());
        assertEquals(64, preview.getQuestionHash().length());
        assertEquals(1, preview.getPageCount());
        assertTrue(new String(service.getPreview(preview.getToken(), 8L).bytes()).startsWith("%PDF-"));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.getPreview(preview.getToken(), 9L)).getCode());
        service.deletePreview(preview.getToken(), 8L);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.getPreview(preview.getToken(), 8L)).getCode());
        verify(questions).findAllById(List.of(3L));
    }

    @Test
    void expiredPreviewIsRemovedByCleanup() throws Exception {
        ExamQuestionRepository questions = mock(ExamQuestionRepository.class);
        ExamPaperDocumentDispatcher dispatcher = mock(ExamPaperDocumentDispatcher.class);
        when(questions.findAllById(anyList())).thenReturn(List.of(question()));
        when(dispatcher.generate(any(), any(), any())).thenReturn(new byte[]{1});
        var service = service(questions, dispatcher, Duration.ZERO, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var preview = service.createPreview(request(), 8L);
        service.cleanupExpired();
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.getPreview(preview.getToken(), 8L)).getCode());
    }

    private ExamPaperPreviewServiceImpl service(ExamQuestionRepository questions,
            ExamPaperDocumentDispatcher dispatcher, Duration ttl, Clock clock) throws Exception {
        Path executable = root.resolve("fake-soffice.sh");
        Files.writeString(executable, """
                #!/bin/sh
                last=""; out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--outdir" ]; then shift; out="$1"; else last="$1"; fi
                  shift
                done
                base=$(basename "$last" .docx)
                printf '%%PDF-1.4\n1 0 obj <</Type /Page>> endobj\n%%%%EOF\n' > "$out/$base.pdf"
                """);
        executable.toFile().setExecutable(true);
        return new ExamPaperPreviewServiceImpl(questions, dispatcher, root, ttl, clock,
                executable.toString(), Duration.ofSeconds(2));
    }

    private ExamQuestion question() {
        ExamQuestion q = new ExamQuestion(); q.setId(3L); q.setStatus(1); q.setType("单选题");
        q.setStem("题干"); q.setBodyJson("{\"options\":[\"A\",\"B\"]}"); q.setAnswerJson("\"A\"");
        return q;
    }

    private CreateRequest request() {
        PaperLayoutRequest layout = new PaperLayoutRequest(); layout.setRenderMode(PaperRenderMode.TEMPLATE);
        layout.setPageSize(PageSize.A3); layout.setOrientation(Orientation.LANDSCAPE);
        layout.setMarginPreset(MarginPreset.BINDING); layout.setColumnsCount(2); layout.setColumnSpace(425);
        layout.setHasBindingLine(true); layout.setHeaderInfo("信息"); layout.setTitleFontSize(50);
        layout.setSubtitleFontSize(24); layout.setBodyFontSize(21);
        SelectedQuestion selected = new SelectedQuestion(); selected.setQuestionId(3L);
        selected.setScore(BigDecimal.ONE); selected.setSortOrder(1);
        CreateRequest request = new CreateRequest(); request.setTitle("测试"); request.setSelectionMode(SelectionMode.MANUAL);
        request.setLayout(layout); request.setQuestions(List.of(selected)); return request;
    }
}
