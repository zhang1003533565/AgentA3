package com.example.appbackend.service.exampaper;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LibreOfficePreviewConverter {
    private final String sofficePath;
    private final Duration timeout;
    private final Path previewRoot;

    public LibreOfficePreviewConverter(String sofficePath, Duration timeout, Path previewRoot) {
        if (sofficePath == null || sofficePath.isBlank()) throw new IllegalArgumentException("soffice path 不能为空");
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("预览转换超时必须在 0 到 10 分钟之间");
        Path normalized = previewRoot.toAbsolutePath().normalize();
        if (normalized.getParent() == null) throw new IllegalArgumentException("预览根目录必须是专用子目录");
        this.sofficePath = sofficePath;
        this.timeout = timeout;
        this.previewRoot = normalized;
        initializeRoot();
    }

    public final void initializeRoot() {
        try {
            Files.createDirectories(previewRoot);
            if (Files.isSymbolicLink(previewRoot) || !Files.isDirectory(previewRoot, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("预览根目录不能是符号链接或普通文件");
            previewRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法创建预览根目录", exception);
        }
    }

    public ConversionResult convert(byte[] docx, Path workDirectory) {
        Path safeWork = createSafeDirectory(workDirectory);
        String basename = UUID.randomUUID().toString();
        Path input = safeWork.resolve(basename + ".docx");
        Path output = safeWork.resolve(basename + ".pdf");
        Path profile = safeWork.resolve(UUID.randomUUID().toString());
        Process process = null;
        try {
            createSafeDirectory(profile);
            Files.write(input, docx, StandardOpenOption.CREATE_NEW);
            List<String> command = List.of(sofficePath, "--headless",
                    "-env:UserInstallation=" + profile.toUri(), "--convert-to", "pdf",
                    "--outdir", safeWork.toString(), input.toString());
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(output))
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换失败");
            byte[] pdf = Files.readAllBytes(output);
            try (PDDocument document = Loader.loadPDF(pdf)) {
                int pageCount = document.getNumberOfPages();
                if (pageCount < 1) throw new IOException("PDF has no pages");
                return new ConversionResult(pdf, pageCount);
            } catch (IOException malformed) {
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换结果不是有效 PDF");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(Result.ERROR_CODE, "LibreOffice 不可用，请检查预览服务配置");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null && process.isAlive()) terminate(process);
            throw new BusinessException(Result.ERROR_CODE, "试卷预览转换被中断");
        } finally {
            deleteRecursively(profile);
            deleteFile(input);
            deleteFile(output);
        }
    }

    private void terminate(Process process) {
        try {
            process.descendants().forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
        } catch (RuntimeException ignored) {
            // Some restricted runtimes deny process-tree enumeration; the direct process is still forcibly killed.
        }
        process.destroyForcibly();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.isAlive())
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换进程无法终止");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(Result.ERROR_CODE, "等待预览转换进程终止时被中断");
        }
    }

    public Path createSafeDirectory(Path target) {
        Path normalized = normalizedDescendant(target);
        Path current = previewRoot;
        try {
            Path relative = previewRoot.relativize(normalized);
            for (Path segment : relative) {
                current = current.resolve(segment);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                        throw new IllegalArgumentException("预览路径不能经过符号链接或普通文件");
                } else {
                    Files.createDirectory(current);
                }
            }
            if (!current.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(
                    previewRoot.toRealPath(LinkOption.NOFOLLOW_LINKS)))
                throw new IllegalArgumentException("预览路径逃逸配置根目录");
            return current;
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法安全创建预览目录", exception);
        }
    }

    public void deleteRecursively(Path target) {
        if (target == null || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Path normalized = normalizedDescendant(target);
        if (containsSymlink(normalized)) throw new IllegalArgumentException("拒绝清理包含符号链接的预览路径");
        try (var paths = Files.walk(normalized)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(this::deleteFile);
        } catch (IOException ignored) { }
    }

    private boolean containsSymlink(Path target) {
        Path current = previewRoot;
        for (Path segment : previewRoot.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private Path normalizedDescendant(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        if (normalized.equals(previewRoot) || !normalized.startsWith(previewRoot))
            throw new IllegalArgumentException("预览文件操作仅允许在配置的预览根目录子目录内");
        return normalized;
    }

    private void deleteFile(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record ConversionResult(byte[] bytes, int pageCount) { }
}
