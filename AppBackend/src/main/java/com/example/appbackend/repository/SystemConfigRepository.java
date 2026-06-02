package com.example.appbackend.repository;

import com.example.appbackend.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long>, JpaSpecificationExecutor<SystemConfig> {

    Optional<SystemConfig> findByConfigKeyAndStatus(String configKey, Integer status);

    Optional<SystemConfig> findByConfigKey(String configKey);

    List<SystemConfig> findByConfigKeyStartingWithAndStatus(String configKeyPrefix, Integer status);
}
