package com.example.appbackend.repository;

import com.example.appbackend.entity.AiLeaderMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiLeaderMessageRepository extends JpaRepository<AiLeaderMessage, Long> {

    List<AiLeaderMessage> findByLeaderSessionIdOrderByCreateTimeAsc(Long leaderSessionId);

    long countByLeaderSessionId(Long leaderSessionId);
}
