package com.example.appbackend.service.exampaper;

import com.example.appbackend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LibreOfficePreviewConverterTest {
    @TempDir Path root;

    @Test
    void convertsWithArgumentSafeFakeExecutableAndValidatesPdf() throws Exception {
        Path fake = executable("fake soffice.sh", """
                #!/bin/sh
                last=""
                out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--outdir" ]; then shift; out="$1"; else last="$1"; fi
                  shift
                done
                base=$(basename "$last" .docx)
                printf '%%PDF-1.4\n1 0 obj <</Type /Page>> endobj\n%%%%EOF\n' > "$out/$base.pdf"
                """);
        var result = new LibreOfficePreviewConverter(fake.toString(), Duration.ofSeconds(2), root)
                .convert(new byte[]{1,2,3}, root.resolve("user with space/token"));
        assertArrayEquals("%PDF-".getBytes(), java.util.Arrays.copyOf(result.bytes(), 5));
        assertEquals(1, result.pageCount());
        assertFalse(Files.exists(root.resolve("user with space/token").resolve("profile")));
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
    }

    @Test
    void refusesRecursiveDeletionOutsideConfiguredRoot() {
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), root);
        assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(root.getParent()));
        assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(root));
    }

    private Path executable(String name, String body) throws Exception {
        Path file = root.resolve(name); Files.writeString(file, body); file.toFile().setExecutable(true); return file;
    }
}
