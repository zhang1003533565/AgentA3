package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("learning-jpa-test")
class ExamQuestionRepositoryVisibilityTest {

    private final ExamQuestionRepository repository;

    @Autowired
    ExamQuestionRepositoryVisibilityTest(ExamQuestionRepository repository) {
        this.repository = repository;
    }

    @Test
    void visibleQueriesReturnPublicAndOwnedPrivateQuestionsOnly() {
        ExamQuestion publicQuestion = repository.save(question(
                "公共题", ExamQuestion.VISIBILITY_PUBLIC, null));
        ExamQuestion ownedPrivate = repository.save(question(
                "我的私有题", ExamQuestion.VISIBILITY_PRIVATE, 11L));
        ExamQuestion foreignPrivate = repository.save(question(
                "别人的私有题", ExamQuestion.VISIBILITY_PRIVATE, 22L));

        var page = repository.searchVisible(null, null, null, 11L, PageRequest.of(0, 20));
        var candidates = repository.findVisibleActiveCandidates("single_choice", "easy", 11L);
        var selected = repository.findAllVisibleById(List.of(
                publicQuestion.getId(), ownedPrivate.getId(), foreignPrivate.getId()), 11L);

        assertThat(page.getContent()).extracting(ExamQuestion::getStem)
                .containsExactlyInAnyOrder("公共题", "我的私有题");
        assertThat(candidates).extracting(ExamQuestion::getStem)
                .containsExactlyInAnyOrder("公共题", "我的私有题");
        assertThat(selected).extracting(ExamQuestion::getStem)
                .containsExactlyInAnyOrder("公共题", "我的私有题");
        assertThat(repository.findVisibleById(foreignPrivate.getId(), 11L)).isEmpty();
        assertThat(repository.findVisibleById(foreignPrivate.getId(), 22L)).isPresent();
    }

    private ExamQuestion question(String stem, String visibility, Long ownerUserId) {
        ExamQuestion question = new ExamQuestion();
        question.setType("single_choice");
        question.setStem(stem);
        question.setScore(new BigDecimal("5.00"));
        question.setDifficulty("easy");
        question.setBodyJson("{\"options\":[]}");
        question.setAnswerJson("{\"correctOptionKey\":\"A\"}");
        question.setScoringJson("{\"mode\":\"exact\"}");
        question.setRawQuestionJson("{}");
        question.setVisibility(visibility);
        question.setOwnerUserId(ownerUserId);
        question.setCreatedBy(ownerUserId == null ? 1L : ownerUserId);
        return question;
    }
}
