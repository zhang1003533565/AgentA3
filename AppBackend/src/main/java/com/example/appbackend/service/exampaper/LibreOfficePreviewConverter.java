package com.example.appbackend.service.exampaper;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibreOfficePreviewConverter {
    private static final Pattern PDF_PAGE = Pattern.compile("/Type\\s*/Page(?!s)\\b");
    private final String sofficePath;
    private final Duration timeout;
    private final Path previewRoot;

    public LibreOfficePreviewConverter(String sofficePath, Duration timeout, Path previewRoot) {
        this.sofficePath = sofficePath;
        this.timeout = timeout;
        this.previewRoot = previewRoot.toAbsolutePath().normalize();
    }

    public ConversionResult convert(byte[] docx, Path workDirectory) {
        ensureWithinRoot(workDirectory);
        String basename = UUID.randomUUID().toString();
        Path input = workDirectory.resolve(basename + ".docx");
        Path output = workDirectory.resolve(basename + ".pdf");
        Path profile = workDirectory.resolve("profile-" + UUID.randomUUID());
        Process process = null;
        try {
            Files.createDirectories(profile);
            Files.write(input, docx);
            List<String> command = new ArrayList<>();
            command.add(sofficePath);
            command.add("--headless");
            command.add("-env:UserInstallation=" + profile.toUri());
            command.add("--convert-to");
            command.add("pdf");
            command.add("--outdir");
            command.add(workDirectory.toString());
            command.add(input.toString());
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换失败");
            }
            byte[] pdf = Files.readAllBytes(output);
            if (pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new BusinessException(Result.ERROR_CODE, "试卷预览转换结果不是有效 PDF");
            }
            Matcher matcher = PDF_PAGE.matcher(new String(pdf, StandardCharsets.ISO_8859_1));
            int pages = 0;
            while (matcher.find()) pages++;
            if (pages < 1) {
                throw new BusinessException(Result.ERROR_CODE, "试卷预览 PDF 不包含页面");
            }
            return new ConversionResult(pdf, pages);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(Result.ERROR_CODE, "LibreOffice 不可用，请检查预览服务配置");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new BusinessException(Result.ERROR_CODE, "试卷预览转换被中断");
        } finally {
            deleteRecursively(profile);
            try { Files.deleteIfExists(input); } catch (IOException ignored) {}
        }
    }

    public void deleteRecursively(Path target) {
        if (target == null || !Files.exists(target)) return;
        ensureWithinRoot(target);
        try (var paths = Files.walk(target)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private void ensureWithinRoot(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        if (normalized.equals(previewRoot) || !normalized.startsWith(previewRoot)) {
            throw new IllegalArgumentException("预览文件操作仅允许在配置的预览根目录子目录内");
        }
    }

    public record ConversionResult(byte[] bytes, int pageCount) {}
}
