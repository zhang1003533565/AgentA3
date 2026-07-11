package com.example.appbackend.service.exampaper;

import com.example.appbackend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LibreOfficePreviewConverterTest {
    @TempDir Path root;

    @Test
    void convertsWithArgumentSafeFakeExecutableAndValidatesPdf() throws Exception {
        Path fixture = pdf("fixture.pdf", 2);
        Path fake = executable("fake soffice.sh", """
                #!/bin/sh
                last=""
                out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--outdir" ]; then shift; out="$1"; else last="$1"; fi
                  shift
                done
                base=$(basename "$last" .docx)
                cp '%s' "$out/$base.pdf"
                """.formatted(fixture));
        var result = new LibreOfficePreviewConverter(fake.toString(), Duration.ofSeconds(2), root)
                .convert(new byte[]{1,2,3}, root.resolve("user with space/token"));
        assertArrayEquals("%PDF-".getBytes(), java.util.Arrays.copyOf(result.bytes(), 5));
        assertEquals(2, result.pageCount());
        try (var files = Files.list(root.resolve("user with space/token"))) {
            assertEquals(0, files.count(), "converter must remove input, output and profile artifacts");
        }
    }

    @Test
    void reportsUnavailableTimeoutAndInvalidConversion() throws Exception {
        Path dir = root.resolve("u/t");
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(root.resolve("missing").toString(), Duration.ofMillis(50), root)
                        .convert(new byte[0], dir)).getMessage().contains("不可用"));
        Path slow = executable("slow.sh", "#!/bin/sh\nsleep 2\n");
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(slow.toString(), Duration.ofMillis(50), root)
                        .convert(new byte[0], root.resolve("u/slow"))).getMessage().contains("超时"));
        Path bad = executable("bad.sh", "#!/bin/sh\nexit 3\n");
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(bad.toString(), Duration.ofSeconds(1), root)
                        .convert(new byte[0], root.resolve("u/bad"))).getMessage().contains("失败"));
        Path truncated = root.resolve("truncated.pdf"); Files.writeString(truncated, "%PDF-1.4 truncated");
        Path malformed = executable("malformed.sh", """
                #!/bin/sh
                last=""; out=""
                while [ "$#" -gt 0 ]; do if [ "$1" = "--outdir" ]; then shift; out="$1"; else last="$1"; fi; shift; done
                base=$(basename "$last" .docx); cp '%s' "$out/$base.pdf"
                """.formatted(truncated));
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(malformed.toString(), Duration.ofSeconds(1), root)
                        .convert(new byte[0], root.resolve("u/malformed"))).getMessage().contains("有效 PDF"));
        try (var files = Files.list(root.resolve("u/malformed"))) { assertEquals(0, files.count()); }
    }

    @Test
    void refusesRecursiveDeletionOutsideConfiguredRoot() {
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), root);
        assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(root.getParent()));
        assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(root));
    }

    @Test
    void refusesSymlinkEscape() throws Exception {
        Path outside = Files.createTempDirectory("outside-preview");
        Path user = root.resolve("8"); Files.createDirectories(user);
        Path link = user.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
            LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), root);
            assertThrows(IllegalArgumentException.class, () -> converter.createSafeDirectory(link.resolve("token")));
            assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(link));
        } finally { Files.deleteIfExists(link); Files.deleteIfExists(outside); }
    }

    private Path executable(String name, String body) throws Exception {
        Path file = root.resolve(name); Files.writeString(file, body); file.toFile().setExecutable(true); return file;
    }

    private Path pdf(String name, int pages) throws Exception {
        Path file = root.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            document.save(file.toFile());
        }
        return file;
    }
}
