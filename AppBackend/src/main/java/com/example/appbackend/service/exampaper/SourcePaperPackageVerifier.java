package com.example.appbackend.service.exampaper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

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
    private static final String REL_BASE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    private static final Map<String, RelationshipContract> FIXED_RELATIONSHIPS = Map.of(
            "rId8", new RelationshipContract(REL_BASE + "header", "header1.xml", "headerReference"),
            "rId9", new RelationshipContract(REL_BASE + "header", "header2.xml", "headerReference"),
            "rId10", new RelationshipContract(REL_BASE + "footer", "footer1.xml", "footerReference"),
            "rId11", new RelationshipContract(REL_BASE + "footer", "footer2.xml", "footerReference"));

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
        Document settings = parse(entries.get("word/settings.xml"), "word/settings.xml");
        NodeList evenOdd = settings.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "evenAndOddHeaders");
        if (evenOdd.getLength() != 1) throw new IllegalArgumentException("settings 必须且只能包含一个 evenAndOddHeaders");

        Map<String, RelationshipDefinition> definitions = new HashMap<>();
        NodeList relations = relationships.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
        for (int index = 0; index < relations.getLength(); index++) {
            Element relation = (Element) relations.item(index);
            definitions.put(relation.getAttribute("Id"), new RelationshipDefinition(
                    relation.getAttribute("Type"), relation.getAttribute("Target")));
        }
        for (Map.Entry<String, RelationshipContract> fixed : FIXED_RELATIONSHIPS.entrySet()) {
            RelationshipDefinition actual = definitions.get(fixed.getKey());
            RelationshipContract expected = fixed.getValue();
            if (actual == null || !actual.type().equals(expected.type()) || !actual.target().equals(expected.target())) {
                throw new IllegalArgumentException("固定关系偏离源码: " + fixed.getKey());
            }
            requireReference(document, expected.referenceElement(), fixed.getKey());
            if (!entries.containsKey("word/" + expected.target())) {
                throw new IllegalArgumentException("固定关系目标缺失: " + expected.target());
            }
        }
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

    private static void requireReference(Document document, String elementName, String expectedId) {
        NodeList references = document.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", elementName);
        int found = 0;
        for (int index = 0; index < references.getLength(); index++) {
            Element reference = (Element) references.item(index);
            String id = reference.getAttributeNS(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            if (expectedId.equals(id)) found++;
        }
        if (found != 1) throw new IllegalArgumentException("固定页眉页脚引用缺失或重复: " + expectedId);
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
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(xml));
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

    private record RelationshipContract(String type, String target, String referenceElement) {}

    private record RelationshipDefinition(String type, String target) {}
}
