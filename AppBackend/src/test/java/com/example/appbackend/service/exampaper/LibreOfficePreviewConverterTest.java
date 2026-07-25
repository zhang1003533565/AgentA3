package com.example.appbackend.service.exampaper;

import com.example.appbackend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LibreOfficePreviewConverterTest {
    @TempDir Path root;

    @Test
    void resolvesBareSofficeCommandFromAvailableFallback() throws Exception {
        String command = "agent-a3-test-soffice";
        Path fallback = executable(command, "#!/bin/sh\nexit 0\n");

        assertEquals(fallback.toString(), LibreOfficePreviewConverter.resolveSofficePath(
                command, List.of(root.resolve("missing"), fallback)));
        assertEquals(root.resolve("explicit-soffice").toString(), LibreOfficePreviewConverter.resolveSofficePath(
                root.resolve("explicit-soffice").toString(), List.of(fallback)));
    }

    @Test
    void includesStandardWindowsLibreOfficeInstallLocations() {
        List<Path> candidates = LibreOfficePreviewConverter.defaultSofficeCandidates();

        assertTrue(candidates.contains(Path.of("C:\\Program Files\\LibreOffice\\program\\soffice.com")));
        assertTrue(candidates.contains(Path.of("C:\\Program Files\\LibreOffice\\program\\soffice.exe")));
        assertTrue(candidates.contains(Path.of("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.com")));
        assertTrue(candidates.contains(Path.of("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe")));
    }

    @Test
    void substitutesMissingSourceFontsOnlyInsidePreviewPackageAttributes() throws Exception {
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(source)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:rFonts w:eastAsia=\"宋体\"/><w:t>题目提到宋体和黑体</w:t>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/media/image1.png"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }

        byte[] converted = LibreOfficePreviewConverter.substituteCjkFontsForPreview(source.toByteArray());
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(converted))) {
            assertEquals("word/document.xml", zip.getNextEntry().getName());
            String xml = new String(zip.readAllBytes());
            assertTrue(xml.contains("w:eastAsia=\"Songti SC\""));
            assertTrue(xml.contains("<w:t>题目提到宋体和黑体</w:t>"));
            assertEquals("word/media/image1.png", zip.getNextEntry().getName());
            assertArrayEquals(new byte[]{1, 2, 3}, zip.readAllBytes());
        }
    }

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
        Path previewRoot = root.resolve("preview");
        var result = new LibreOfficePreviewConverter(fake.toString(), Duration.ofSeconds(2), previewRoot)
                .convert(new byte[]{1,2,3}, previewRoot.resolve("user with space/token"));
        assertArrayEquals("%PDF-".getBytes(), java.util.Arrays.copyOf(result.bytes(), 5));
        assertEquals(2, result.pageCount());
        try (var files = Files.list(previewRoot.resolve("user with space/token"))) {
            assertEquals(0, files.count(), "converter must remove input, output and profile artifacts");
        }
    }

    @Test
    void reportsUnavailableTimeoutAndInvalidConversion() throws Exception {
        Path previewRoot = root.resolve("preview");
        Path dir = previewRoot.resolve("u/t");
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(root.resolve("missing").toString(), Duration.ofMillis(50), previewRoot)
                        .convert(new byte[0], dir)).getMessage().contains("不可用"));
        Path slow = executable("slow.sh", "#!/bin/sh\nsleep 2\n");
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(slow.toString(), Duration.ofMillis(50), previewRoot)
                        .convert(new byte[0], previewRoot.resolve("u/slow"))).getMessage().contains("超时"));
        Path bad = executable("bad.sh", "#!/bin/sh\nexit 3\n");
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(bad.toString(), Duration.ofSeconds(1), previewRoot)
                        .convert(new byte[0], previewRoot.resolve("u/bad"))).getMessage().contains("失败"));
        Path truncated = root.resolve("truncated.pdf"); Files.writeString(truncated, "%PDF-1.4 truncated");
        Path malformed = executable("malformed.sh", """
                #!/bin/sh
                last=""; out=""
                while [ "$#" -gt 0 ]; do if [ "$1" = "--outdir" ]; then shift; out="$1"; else last="$1"; fi; shift; done
                base=$(basename "$last" .docx); cp '%s' "$out/$base.pdf"
                """.formatted(truncated));
        assertTrue(assertThrows(BusinessException.class, () ->
                new LibreOfficePreviewConverter(malformed.toString(), Duration.ofSeconds(1), previewRoot)
                        .convert(new byte[0], previewRoot.resolve("u/malformed"))).getMessage().contains("有效 PDF"));
        try (var files = Files.list(previewRoot.resolve("u/malformed"))) { assertEquals(0, files.count()); }
    }

    @Test
    void refusesRecursiveDeletionOutsideConfiguredRoot() {
        Path previewRoot = root.resolve("preview");
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), previewRoot);
        assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(root.getParent()));
        assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(previewRoot));
    }

    @Test
    void refusesSymlinkEscape() throws Exception {
        Path outside = Files.createTempDirectory("outside-preview");
        Path previewRoot = root.resolve("preview");
        LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), previewRoot);
        Path user = previewRoot.resolve("8"); Files.createDirectories(user);
        Path link = user.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
            assertThrows(IllegalArgumentException.class, () -> converter.createSafeDirectory(link.resolve("token")));
            assertThrows(IllegalArgumentException.class, () -> converter.deleteRecursively(link));
        } finally { Files.deleteIfExists(link); Files.deleteIfExists(outside); }
    }

    @Test
    void enforcesDedicatedOwnedRoot() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new LibreOfficePreviewConverter("x",
                Duration.ofSeconds(1), Path.of(System.getProperty("java.io.tmpdir"))));
        assertThrows(IllegalArgumentException.class, () -> new LibreOfficePreviewConverter("x",
                Duration.ofSeconds(1), Path.of("/")));
        Path foreign = root.resolve("foreign"); Files.createDirectories(foreign); Files.writeString(foreign.resolve("data"), "x");
        assertThrows(IllegalArgumentException.class, () ->
                new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), foreign));
        Path owned = root.resolve("owned");
        assertDoesNotThrow(() -> new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), owned));
        assertDoesNotThrow(() -> new LibreOfficePreviewConverter("x", Duration.ofSeconds(1), owned));
        assertEquals(LibreOfficePreviewConverter.OWNER_CONTENT,
                Files.readString(owned.resolve(LibreOfficePreviewConverter.OWNER_MARKER)));
    }

    @Test
    void timeoutTerminatesSpawnedParentAndChild() throws Exception {
        boolean descendantsSupported = descendantsEnumerationSupported();
        Path parentPid = root.resolve("parent.pid"); Path childPid = root.resolve("child.pid");
        Path script = executable("tree.sh", """
                #!/bin/sh
                echo $$ > '%s'
                sleep 30 &
                echo $! > '%s'
                wait
                """.formatted(parentPid, childPid));
        Path previewRoot = root.resolve("tree-preview");
        assertThrows(BusinessException.class, () -> new LibreOfficePreviewConverter(script.toString(),
                Duration.ofMillis(500), previewRoot).convert(new byte[0], previewRoot.resolve("8/token")));
        long parent = Long.parseLong(Files.readString(parentPid).trim());
        long child = Long.parseLong(Files.readString(childPid).trim());
        Thread.sleep(100);
        assertFalse(ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false));
        assumeTrue(descendantsSupported, "runtime denies ProcessHandle descendant enumeration");
        assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
    }

    private boolean descendantsEnumerationSupported() throws Exception {
        Process probe = new ProcessBuilder("/bin/sh", "-c", "sleep 0.2 & wait").start();
        try {
            Thread.sleep(50);
            return probe.descendants().findAny().isPresent();
        } catch (RuntimeException denied) {
            return false;
        } finally {
            probe.destroyForcibly();
        }
    }

    private Path executable(String name, String body) throws Exception {
        Path file = root.resolve(name); Files.writeString(file, body.stripLeading()); file.toFile().setExecutable(true); return file;
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
