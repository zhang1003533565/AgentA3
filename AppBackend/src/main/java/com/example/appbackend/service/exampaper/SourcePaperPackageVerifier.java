package com.example.appbackend.service.exampaper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Structural verifier for the source-derived WordprocessingML package. */
public final class SourcePaperPackageVerifier {

    public static final Set<String> MUTABLE_PARTS = Set.of(
            "word/document.xml", "word/header1.xml", "word/header2.xml", "word/settings.xml");
    private static final Set<String> REQUIRED = Set.of(
            "[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels",
            "word/header1.xml", "word/header2.xml", "word/footer1.xml", "word/footer2.xml",
            "word/settings.xml", "word/styles.xml", "word/numbering.xml");

    private SourcePaperPackageVerifier() {}

    public static void verify(byte[] docx) {
        Map<String, byte[]> entries = entries(docx);
        for (String required : REQUIRED) {
            if (!entries.containsKey(required)) throw new IllegalArgumentException("DOCX 缺少必需部件: " + required);
        }
        Document document = parse(entries.get("word/document.xml"), "word/document.xml");
        Document relationships = parse(entries.get("word/_rels/document.xml.rels"), "word/_rels/document.xml.rels");
        parse(entries.get("word/header1.xml"), "word/header1.xml");
        parse(entries.get("word/header2.xml"), "word/header2.xml");
        parse(entries.get("word/footer1.xml"), "word/footer1.xml");
        parse(entries.get("word/footer2.xml"), "word/footer2.xml");
        String settings = new String(entries.get("word/settings.xml"), java.nio.charset.StandardCharsets.UTF_8);
        if (!settings.contains("w:evenAndOddHeaders")) throw new IllegalArgumentException("settings 缺少 evenAndOddHeaders");

        Map<String, String> targets = new HashMap<>();
        NodeList relations = relationships.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
        for (int index = 0; index < relations.getLength(); index++) {
            Element relation = (Element) relations.item(index);
            targets.put(relation.getAttribute("Id"), relation.getAttribute("Target"));
        }
        NodeList references = document.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "headerReference");
        validateReferences(references, targets, entries);
        references = document.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "footerReference");
        validateReferences(references, targets, entries);
    }

    public static void verifyPreservedParts(byte[] source, byte[] generated) {
        Map<String, byte[]> before = entries(source);
        Map<String, byte[]> after = entries(generated);
        if (!before.keySet().equals(after.keySet())) throw new IllegalArgumentException("DOCX 部件集合发生变化");
        for (String name : before.keySet()) {
            if (!MUTABLE_PARTS.contains(name) && !MessageDigest.isEqual(hash(before.get(name)), hash(after.get(name)))) {
                throw new IllegalArgumentException("保留部件被修改: " + name);
            }
        }
    }

    private static void validateReferences(NodeList references, Map<String, String> targets,
                                           Map<String, byte[]> entries) {
        for (int index = 0; index < references.getLength(); index++) {
            Element reference = (Element) references.item(index);
            String id = reference.getAttributeNS(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            String target = targets.get(id);
            if (target == null || !entries.containsKey("word/" + target)) {
                throw new IllegalArgumentException("无效页眉页脚关系: " + id);
            }
        }
    }

    private static Map<String, byte[]> entries(byte[] docx) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) entries.put(entry.getName(), input.readAllBytes());
            }
            return entries;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DOCX ZIP 无效", exception);
        }
    }

    private static Document parse(byte[] xml, String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception exception) {
            throw new IllegalArgumentException("OOXML 部件格式无效: " + name, exception);
        }
    }

    private static byte[] hash(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
