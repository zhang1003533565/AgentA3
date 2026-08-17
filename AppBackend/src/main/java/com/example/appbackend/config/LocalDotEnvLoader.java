package com.example.appbackend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LocalDotEnvLoader {

    private LocalDotEnvLoader() {
    }

    public static List<String> loadForWorkingDirectory(Path workingDirectory) {
        List<String> loadedKeys = new ArrayList<>();
        for (Path envFile : candidateFiles(workingDirectory)) {
            loadedKeys.addAll(load(envFile));
        }
        return loadedKeys;
    }

    static List<Path> candidateFiles(Path workingDirectory) {
        Set<Path> files = new LinkedHashSet<>();
        Path current = workingDirectory == null ? Path.of("").toAbsolutePath() : workingDirectory.toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 5; depth++) {
            if ("AppBackend".equalsIgnoreCase(String.valueOf(current.getFileName())) && current.getParent() != null) {
                files.add(current.getParent().resolve(".env").normalize());
                files.add(current.resolve(".env").normalize());
            } else {
                files.add(current.resolve(".env").normalize());
                files.add(current.resolve("AppBackend").resolve(".env").normalize());
            }
            current = current.getParent();
        }
        return List.copyOf(files);
    }

    static List<String> load(Path file) {
        List<String> loadedKeys = new ArrayList<>();
        if (file == null || !Files.isRegularFile(file)) {
            return loadedKeys;
        }
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = line.substring(0, separator).trim();
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    continue;
                }
                if (hasValue(System.getProperty(name)) || hasValue(System.getenv(name))) {
                    continue;
                }
                String value = unquote(line.substring(separator + 1).trim());
                System.setProperty(name, value);
                loadedKeys.add(name);
            }
        } catch (IOException ignored) {
            return loadedKeys;
        }
        return loadedKeys;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
