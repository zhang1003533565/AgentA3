package com.example.appbackend.dto;

import lombok.Data;

/**
 * Word 文档内容分页响应。
 */
public class WordContentDTO {

    @Data
    public static class PageResponse {
        private Long materialId;
        private String fileName;
        private int totalPages;
        private int currentPage;
        private int pageSize;
        private String content;
    }
}
