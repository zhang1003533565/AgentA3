package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.service.exampaper.SourcePaperLayoutResolver.BindingLayoutTokens;
import com.example.appbackend.service.exampaper.SourcePaperLayoutResolver.ResolvedPageLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Java port of generatePaperByTemplate.js: copy the source DOCX and replace named slots only. */
public final class SourcePaperTemplateEngine {

    static final String STATIC_TEMPLATE = "exam-paper-template/static/document.docx";
    static final String DOCUMENT_TEMPLATE = "exam-paper-template/document/document.xml";
    static final String HEADER1_TEMPLATE = "exam-paper-template/head/header1.xml";
    static final String HEADER2_TEMPLATE = "exam-paper-template/head/header2.xml";
    private static final Pattern UNRESOLVED_TOKEN = Pattern.compile("%[^%]+%");

    private final SourcePaperLayoutResolver layoutResolver;
    private final SourcePaperXmlRenderer renderer;

    public SourcePaperTemplateEngine() {
        this(new SourcePaperLayoutResolver(), new SourcePaperXmlRenderer());
    }

    SourcePaperTemplateEngine(SourcePaperLayoutResolver layoutResolver, SourcePaperXmlRenderer renderer) {
        this.layoutResolver = Objects.requireNonNull(layoutResolver);
        this.renderer = Objects.requireNonNull(renderer);
    }

    public byte[] generate(PaperVO paper, DownloadContent content, PaperLayoutConfig layout) {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(layout, "layout");
        ResolvedPageLayout resolved = layoutResolver.resolve(layout);

        String document = resourceText(DOCUMENT_TEMPLATE);
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("%TITLE%", escapeXml(paper.getTitle()));
        replacements.put("%SUBTITLE%", renderer.renderSubtitle(paper, layout));
        replacements.put("%TIME%", "");
        replacements.put("%NAME%", "");
        replacements.put("%SCORE%", renderer.renderScoreTable(paper));
        replacements.put("%PRECAUTIONS%", paragraph(paper.getPrecautions(), layout.getBodyFontSize()));
        replacements.put("%QUESTION%", renderer.renderQuestions(paper, layout));
        replacements.put("%ANSWER%", content == DownloadContent.ANSWER ? renderer.renderAnswers(paper, layout) : "");
        replacements.put("%HEADER%", headerReferences(resolved.hasBindingLine()));
        replacements.put("%PageSetting%", renderer.renderPageSettings(resolved));
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            document = replaceRequired(document, replacement.getKey(), replacement.getValue(), 1);
        }

        String header1 = replaceHeader(resourceText(HEADER1_TEMPLATE), layoutResolver.bindingTokens(layout), "h1");
        String header2 = replaceHeader(resourceText(HEADER2_TEMPLATE), layoutResolver.bindingTokens(layout), "h2");
        requireResolved("word/document.xml", document);
        requireResolved("word/header1.xml", header1);
        requireResolved("word/header2.xml", header2);

        Map<String, byte[]> mutable = new LinkedHashMap<>();
        mutable.put("word/document.xml", document.getBytes(StandardCharsets.UTF_8));
        mutable.put("word/header1.xml", header1.getBytes(StandardCharsets.UTF_8));
        mutable.put("word/header2.xml", header2.getBytes(StandardCharsets.UTF_8));
        byte[] template = resourceBytes(STATIC_TEMPLATE);
        mutable.put("word/settings.xml", settingsWithEvenAndOddHeaders(template));
        byte[] generated = copyWithReplacements(template, mutable);
        SourcePaperPackageVerifier.verify(generated);
        SourcePaperPackageVerifier.verifyPreservedParts(template, generated);
        return generated;
    }

    private String replaceHeader(String template, BindingLayoutTokens tokens, String prefix) {
        String result = template;
        for (Map.Entry<String, String> token : tokens.values().entrySet()) {
            boolean relevant = token.getKey().equals("%information%") || token.getKey().startsWith("%" + prefix);
            if (relevant) {
                int expected = occurrences(template, token.getKey());
                if (expected > 0) result = replaceRequired(result, token.getKey(), escapeHeaderValue(token), expected);
            }
        }
        return result;
    }

    private String escapeHeaderValue(Map.Entry<String, String> token) {
        return token.getKey().equals("%information%") ? escapeXml(token.getValue()) : token.getValue();
    }

    static String replaceRequired(String source, String token, String replacement, int expectedCount) {
        int actual = occurrences(source, token);
        if (actual != expectedCount) {
            throw new IllegalArgumentException("模板占位符 " + token + " 期望 " + expectedCount + " 个，实际 " + actual + " 个");
        }
        return source.replace(token, Objects.requireNonNullElse(replacement, ""));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String headerReferences(boolean binding) {
        if (!binding) return "";
        return "<w:headerReference w:type=\"even\" r:id=\"rId9\"/>"
                + "<w:headerReference w:type=\"default\" r:id=\"rId8\"/>"
                + "<w:footerReference w:type=\"even\" r:id=\"rId11\"/>"
                + "<w:footerReference w:type=\"default\" r:id=\"rId10\"/>";
    }

    private static String paragraph(String value, int fontSize) {
        if (value == null || value.isBlank()) return "";
        return "<w:p><w:r><w:rPr><w:rFonts w:eastAsia=\"宋体\" w:hint=\"eastAsia\"/>"
                + "<w:sz w:val=\"" + fontSize + "\"/><w:szCs w:val=\"" + fontSize + "\"/>"
                + "</w:rPr><w:t>" + escapeXml(value) + "</w:t></w:r></w:p>";
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void requireResolved(String name, String xml) {
        if (UNRESOLVED_TOKEN.matcher(xml).find()) {
            throw new IllegalArgumentException(name + " 存在未解析模板占位符");
        }
    }

    private static byte[] copyWithReplacements(byte[] source, Map<String, byte[]> replacements) {
        Map<String, Boolean> found = new LinkedHashMap<>();
        replacements.keySet().forEach(name -> found.put(name, false));
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ZipEntry copied = new ZipEntry(entry.getName());
                copied.setTime(entry.getTime());
                output.putNextEntry(copied);
                byte[] replacement = replacements.get(entry.getName());
                if (replacement != null) {
                    output.write(replacement);
                    found.put(entry.getName(), true);
                } else if (!entry.isDirectory()) {
                    input.transferTo(output);
                }
                output.closeEntry();
            }
            for (Map.Entry<String, Boolean> item : found.entrySet()) {
                if (!item.getValue()) throw new IllegalArgumentException("基础模板缺少部件: " + item.getKey());
            }
            output.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成源码模板 DOCX 失败", exception);
        }
    }

    private static byte[] settingsWithEvenAndOddHeaders(byte[] template) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(template))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals("word/settings.xml")) {
                    String settings = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    if (!settings.contains("w:evenAndOddHeaders")) {
                        settings = replaceRequired(settings, "</w:settings>",
                                "<w:evenAndOddHeaders/></w:settings>", 1);
                    }
                    return settings.getBytes(StandardCharsets.UTF_8);
                }
            }
            throw new IllegalArgumentException("基础模板缺少部件: word/settings.xml");
        } catch (IOException exception) {
            throw new IllegalStateException("读取基础模板 settings 失败", exception);
        }
    }

    private static String resourceText(String name) {
        return new String(resourceBytes(name), StandardCharsets.UTF_8);
    }

    private static byte[] resourceBytes(String name) {
        try (InputStream input = SourcePaperTemplateEngine.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("缺少模板资源: " + name);
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("读取模板资源失败: " + name, exception);
        }
    }
}
