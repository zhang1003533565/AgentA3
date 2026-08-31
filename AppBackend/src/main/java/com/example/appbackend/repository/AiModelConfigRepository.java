package com.example.appbackend.repository;

import com.example.appbackend.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AiModelConfig entity
 */
@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {
}
