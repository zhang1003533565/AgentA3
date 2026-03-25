package com.example.appbackend.repository;

import com.example.appbackend.entity.MapConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MapConfigRepository extends JpaRepository<MapConfig, Long> {

    Optional<MapConfig> findByConfigKey(String configKey);

    @Query("SELECT c FROM MapConfig c WHERE c.configKey = :configKey")
    Optional<MapConfig> findConfigByKey(@Param("configKey") String configKey);
}
