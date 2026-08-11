package com.example.appbackend.service;

public record ParsedFileContent(
        String text,
        int textLength,
        boolean truncated,
        int pageCount,
        int slideCount,
        int paragraphCount
) {
}
