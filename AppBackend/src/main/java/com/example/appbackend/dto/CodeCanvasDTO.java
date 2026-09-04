package com.example.appbackend.dto;

import lombok.Data;

/**
 * 代码画布（Code Canvas）相关 DTO。
 * 用户在左侧输入后端程序代码（实体类/Controller 等），AI 分析后生成前端预览页面（单文件 HTML）。
 */
public class CodeCanvasDTO {

    @Data
    public static class GenerateRequest {
        /** 用户输入的后端程序代码（Java 实体类、Controller、SQL 建表语句等） */
        private String code;

        /** 可选：用户对页面的补充描述/风格要求 */
        private String requirement;

        /** 可选：页面标题，不传则由 AI 根据代码推断 */
        private String title;
    }

    @Data
    public static class GenerateResponse {
        /** 页面标题 */
        private String title;

        /** AI 推断出的业务模块说明 */
        private String summary;

        /** 识别到的实体/字段信息（纯文本描述，用于前端展示） */
        private String entities;

        /** 生成的完整单文件 HTML（可直接放入 iframe srcDoc 渲染） */
        private String html;
    }
}
