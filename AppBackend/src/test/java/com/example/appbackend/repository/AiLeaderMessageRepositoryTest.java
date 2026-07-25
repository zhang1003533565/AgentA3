package com.example.appbackend.repository;

import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("learning-jpa-test")
class AiLeaderMessageRepositoryTest {

    private final AiLeaderMessageRepository messageRepository;
    private final AiLeaderSessionRepository sessionRepository;
    private final EntityManager entityManager;

    @Autowired
    AiLeaderMessageRepositoryTest(AiLeaderMessageRepository messageRepository,
                                  AiLeaderSessionRepository sessionRepository,
                                  EntityManager entityManager) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.entityManager = entityManager;
    }

    @Test
    void sameTimestampMessagesAreReturnedInIdOrderForDeterministicRebuild()
            throws Exception {
        AiLeaderSession session = new AiLeaderSession();
        session.setSessionId("learning-wf-same-time");
        session.setUserId(42L);
        session.setTitle("Python 个性化资源");
        session = sessionRepository.saveAndFlush(session);

        AiLeaderMessage user = messageRepository.save(message(
                session.getId(), AiLeaderMessage.ROLE_USER, "列表切片"));
        AiLeaderMessage firstAssistant = messageRepository.save(message(
                session.getId(), AiLeaderMessage.ROLE_ASSISTANT, "第一版资源"));
        AiLeaderMessage secondAssistant = messageRepository.saveAndFlush(message(
                session.getId(), AiLeaderMessage.ROLE_ASSISTANT, "第二版资源"));
        LocalDateTime sharedTime = LocalDateTime.of(2026, 7, 15, 12, 0);
        entityManager.createNativeQuery(
                        "UPDATE ai_leader_message SET create_time = :createTime "
                                + "WHERE leader_session_id = :sessionId")
                .setParameter("createTime", Timestamp.valueOf(sharedTime))
                .setParameter("sessionId", session.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        List<AiLeaderMessage> ordered = messageRepository
                .findByLeaderSessionIdOrderByCreateTimeAscIdAsc(session.getId());

        assertThat(ordered).extracting(AiLeaderMessage::getId)
                .containsExactly(user.getId(), firstAssistant.getId(), secondAssistant.getId());
    }

    private AiLeaderMessage message(Long sessionId, String role, String content) {
        AiLeaderMessage message = new AiLeaderMessage();
        message.setLeaderSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
