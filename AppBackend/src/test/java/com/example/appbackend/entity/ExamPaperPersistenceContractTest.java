package com.example.appbackend.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamPaperPersistenceContractTest {

    @Test
    void paperContractContainsStableSnapshotAndLayoutFields() throws Exception {
        assertNotNull(ExamPaper.class.getDeclaredField("pageSize"));
        assertNotNull(ExamPaper.class.getDeclaredField("orientation"));
        assertNotNull(ExamPaper.class.getDeclaredField("columnsCount"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("bodyJson"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("answerJson"));
        assertNotNull(ExamPaperQuestion.class.getDeclaredField("sortOrder"));
    }
}
