package com.example.appbackend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionConfigurationTest {

    private static final Pattern YAML_PROPERTY = Pattern.compile("^(\\s*)([A-Za-z0-9-]+):(?:\\s*(.*))?$");
    private static final Pattern QUOTED_FIELD = Pattern.compile("'([^']*)'");
    private static final Set<String> DATABASE_SECRET_KEYS = Set.of(
            "jwt.secret",
            "tencent.map.key",
            "aliyun.oss.access-key-id",
            "aliyun.oss.access-key-secret"
    );
    private static final Pattern REMOVED_APP_MAP_KEY_REFERENCE = Pattern.compile(
            "\\b(?:AMAP_APP_KEY|AMAP_WEB_KEY|MAP_KEY)\\b"
    );

    @Test
    void applicationYamlUsesOnlyRequiredEnvironmentPlaceholdersForSubmissionSecrets() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        Map<String, String> values = flattenYaml(yaml);
        Map<String, String> expected = Map.ofEntries(
                Map.entry("jwt.secret", "${JWT_SECRET:dev-local-jwt-secret-change-before-production-2026}"),
                Map.entry("jwt.expiration", "${JWT_EXPIRATION_MS:86400000}"),
                Map.entry("tencent.map.key", "${TENCENT_MAP_KEY:}"),
                Map.entry("tencent.cos.secret-id", "${TENCENT_COS_SECRET_ID:}"),
                Map.entry("tencent.cos.secret-key", "${TENCENT_COS_SECRET_KEY:}"),
                Map.entry("amap.app.key", "${AMAP_APP_KEY:}"),
                Map.entry("amap.web.key", "${AMAP_WEB_KEY:}"),
                Map.entry("aliyun.oss.access-key-id", "${ALIYUN_OSS_ACCESS_KEY_ID:}"),
                Map.entry("aliyun.oss.access-key-secret", "${ALIYUN_OSS_ACCESS_KEY_SECRET:}")
        );

        expected.forEach((key, value) -> assertEquals(value, values.get(key), key));
    }

    @Test
    void dataSqlKeepsSecretConfigSeedsEmptyDisabledAndDemoIdentitySynthetic() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/data.sql"));

        for (String key : DATABASE_SECRET_KEYS) {
            String row = singleConfigRow(sql, key);
            List<String> fields = quotedFields(row);
            assertEquals("", fields.get(1), key + " must not have a seeded value");
            assertTrue(row.matches(".*\\s0,\\s*NOW\\(\\),\\s*NOW\\(\\)\\)[,;]?$"),
                    key + " must remain disabled in seed data");
        }

        String demoStudent = sql.lines()
                .filter(line -> line.startsWith("(4,") && quotedFields(line).size() == 9)
                .findFirst()
                .orElseThrow();
        List<String> fields = quotedFields(demoStudent);
        assertEquals("A3演示学生", fields.get(2));
        assertEquals("13800000000", fields.get(3));
        assertEquals("a3-demo@example.invalid", fields.get(4));
        assertEquals("", fields.get(5));
        assertEquals("A3DEMO001", fields.get(6));
    }

    @Test
    void appFrontendKeepsRemovedMapKeyExportsAndReferencesAbsent() throws Exception {
        Path frontendRoot = Path.of("../mini_program_app").toAbsolutePath().normalize();
        String config = Files.readString(frontendRoot.resolve("utils/config.js"));
        assertFalse(config.contains("export const AMAP_APP_KEY"));
        assertFalse(config.contains("export const AMAP_WEB_KEY"));
        assertFalse(Pattern.compile("(?i)\\b[0-9a-f]{24,}\\b").matcher(config).find());
        assertTrue(config.contains("export function getApiBaseUrl()"));
        assertTrue(config.contains("export const BASE_URL = getApiBaseUrl()"));

        List<Path> references = new ArrayList<>();
        for (Path source : frontendSources(frontendRoot)) {
            if (REMOVED_APP_MAP_KEY_REFERENCE.matcher(Files.readString(source)).find()) {
                references.add(frontendRoot.relativize(source));
            }
        }
        assertTrue(references.isEmpty(), () -> "Removed map-key references found: " + references);
    }

    @Test
    void jwtUtilContainsNoFixedDefaultSecret() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/example/appbackend/util/JwtUtil.java"));
        assertFalse(source.contains("DEFAULT_SECRET"));
        assertFalse(Pattern.compile("getValue\\(\\\"jwt\\.secret\\\",\\s*\\\"").matcher(source).find());
        assertTrue(source.contains("getValue(\"jwt.secret\", null)"));
    }

    private static Map<String, String> flattenYaml(String yaml) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> path = new ArrayList<>();
        for (String line : yaml.lines().toList()) {
            Matcher matcher = YAML_PROPERTY.matcher(line);
            if (!matcher.matches()) continue;
            int level = matcher.group(1).length() / 2;
            while (path.size() > level) path.remove(path.size() - 1);
            path.add(matcher.group(2));
            values.put(String.join(".", path), matcher.group(3) == null ? "" : matcher.group(3).trim());
        }
        return values;
    }

    private static String singleConfigRow(String sql, String key) {
        List<String> rows = sql.lines()
                .filter(line -> {
                    List<String> fields = quotedFields(line);
                    return !fields.isEmpty() && key.equals(fields.get(0));
                })
                .toList();
        assertEquals(1, rows.size(), key + " must have exactly one seed row");
        return rows.get(0);
    }

    private static List<String> quotedFields(String line) {
        return QUOTED_FIELD.matcher(line).results().map(result -> result.group(1)).toList();
    }

    private static List<Path> frontendSources(Path frontendRoot) throws IOException {
        Path unpackage = frontendRoot.resolve("unpackage");
        try (Stream<Path> paths = Files.walk(frontendRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(unpackage))
                    .filter(path -> !containsSegment(path, "node_modules"))
                    .filter(SubmissionConfigurationTest::isFrontendSource)
                    .toList();
        }
    }

    private static boolean containsSegment(Path path, String segment) {
        for (Path part : path) {
            if (segment.equals(part.toString())) return true;
        }
        return false;
    }

    private static boolean isFrontendSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".js") || name.endsWith(".vue") || name.endsWith(".ts")
                || name.endsWith(".tsx") || name.endsWith(".jsx");
    }
}
