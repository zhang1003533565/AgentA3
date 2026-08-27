package com.example.appbackend.repository;

import com.example.appbackend.entity.AiLeaderGeneratedExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AiLeaderGeneratedExportRepository extends JpaRepository<AiLeaderGeneratedExport, Long> {

    Optional<AiLeaderGeneratedExport> findByMessageIdAndStorageKey(Long messageId, String storageKey);

    Optional<AiLeaderGeneratedExport> findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
            Long userId,
            Long leaderSessionId,
            Long messageId,
            String storageKey,
            String status
    );

    List<AiLeaderGeneratedExport> findByUserIdAndLeaderSessionId(Long userId, Long leaderSessionId);
}
