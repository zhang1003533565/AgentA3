package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamPaperDTO.*;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.exampaper.ExamPaperDocumentDispatcher;
import com.example.appbackend.service.exampaper.LibreOfficePreviewConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.UUID;

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
        AdjustableClock clock = new AdjustableClock(Instant.EPOCH);
        var service = service(questions, dispatcher, Duration.ofSeconds(1), clock);
        var preview = service.createPreview(request(), 8L);
        clock.instant = Instant.EPOCH.plusSeconds(2);
        service.cleanupExpired();
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.getPreview(preview.getToken(), 8L)).getCode());
    }

    @Test
    void conversionFailureCleansWholeTokenDirectory() {
        ExamQuestionRepository questions = mock(ExamQuestionRepository.class);
        ExamPaperDocumentDispatcher dispatcher = mock(ExamPaperDocumentDispatcher.class);
        LibreOfficePreviewConverter converter = mock(LibreOfficePreviewConverter.class);
        when(questions.findAllById(anyList())).thenReturn(List.of(question()));
        when(dispatcher.generate(any(), any(), any())).thenReturn(new byte[]{1});
        when(converter.convert(any(), any())).thenThrow(new BusinessException(500, "转换失败"));
        var service = new ExamPaperPreviewServiceImpl(questions, dispatcher, converter, root,
                Duration.ofMinutes(30), Clock.systemUTC());
        assertThrows(BusinessException.class, () -> service.createPreview(request(), 8L));
        verify(converter).deleteRecursively(argThat(path -> path.startsWith(root.resolve("8"))));
    }

    @Test
    void canonicalHashesAreStableAndChangeForRenderedFields() throws Exception {
        ExamQuestionRepository questions = mock(ExamQuestionRepository.class);
        ExamPaperDocumentDispatcher dispatcher = mock(ExamPaperDocumentDispatcher.class);
        when(questions.findAllById(anyList())).thenReturn(List.of(question()));
        when(dispatcher.generate(any(), any(), any())).thenReturn(new byte[]{1});
        var service = service(questions, dispatcher, Duration.ofMinutes(30), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var first = service.createPreview(request(), 8L);
        var second = service.createPreview(request(), 8L);
        assertEquals(first.getConfigurationHash(), second.getConfigurationHash());
        assertEquals(first.getQuestionHash(), second.getQuestionHash());
        CreateRequest changed = request(); changed.setPrecautions("新的注意事项");
        assertNotEquals(first.getConfigurationHash(), service.createPreview(changed, 8L).getConfigurationHash());
        ExamQuestion changedQuestion = question(); changedQuestion.setAnalysis("变化");
        when(questions.findAllById(anyList())).thenReturn(List.of(changedQuestion));
        assertNotEquals(first.getQuestionHash(), service.createPreview(request(), 8L).getQuestionHash());
    }

    @Test
    void cleanupRemovesOnlyOldUuidOrphans() throws Exception {
        ExamQuestionRepository questions = mock(ExamQuestionRepository.class);
        ExamPaperDocumentDispatcher dispatcher = mock(ExamPaperDocumentDispatcher.class);
        var service = service(questions, dispatcher, Duration.ofSeconds(1), Clock.fixed(Instant.ofEpochSecond(10), ZoneOffset.UTC));
        Path orphan = root.resolve("8").resolve(UUID.randomUUID().toString()); Files.createDirectories(orphan);
        Files.setLastModifiedTime(orphan, java.nio.file.attribute.FileTime.from(Instant.EPOCH));
        Path unrelated = root.resolve("8").resolve("not-a-token"); Files.createDirectories(unrelated);
        service.cleanupExpired();
        assertFalse(Files.exists(orphan)); assertTrue(Files.exists(unrelated));
    }

    private ExamPaperPreviewServiceImpl service(ExamQuestionRepository questions,
            ExamPaperDocumentDispatcher dispatcher, Duration ttl, Clock clock) throws Exception {
        Path executable = root.resolve("fake-soffice.sh");
        Path fixture = root.resolve("fixture.pdf");
        try (PDDocument document = new PDDocument()) { document.addPage(new PDPage()); document.save(fixture.toFile()); }
        Files.writeString(executable, """
                #!/bin/sh
                last=""; out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--outdir" ]; then shift; out="$1"; else last="$1"; fi
                  shift
                done
                base=$(basename "$last" .docx)
                cp '%s' "$out/$base.pdf"
                """.formatted(fixture));
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

    private static final class AdjustableClock extends Clock {
        private Instant instant;
        private AdjustableClock(Instant instant) { this.instant = instant; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
    }
}
