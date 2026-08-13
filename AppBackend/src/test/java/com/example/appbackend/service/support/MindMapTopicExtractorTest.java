package com.example.appbackend.service.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MindMapTopicExtractorTest {

    @Test
    void extractsTopicFromUserGenerationRequest() {
        String topic = MindMapTopicExtractor.extract(
                "",
                "请帮我生成一份Linux学习路线的思维导图",
                "",
                ""
        );

        Assertions.assertEquals("Linux学习路线", topic);
    }

    @Test
    void prefersCleanCourseFileNameWhenAvailable() {
        String topic = MindMapTopicExtractor.extract(
                "",
                "",
                "第一章 课程导论\n第二章 操作系统与网络基础",
                "计算机课程体系.pdf"
        );

        Assertions.assertEquals("计算机课程体系", topic);
    }

    @Test
    void avoidsStudentNameAndLongNumbersWhenFileNameIsNoisy() {
        String topic = MindMapTopicExtractor.extract(
                "",
                "",
                "星核创研项目汇报\n项目背景、研究目标、技术路线、阶段成果",
                "成理-716-星核创研-吴洪宇（2025170203230001）.docx"
        );

        Assertions.assertEquals("星核创研项目", topic);
    }
}
