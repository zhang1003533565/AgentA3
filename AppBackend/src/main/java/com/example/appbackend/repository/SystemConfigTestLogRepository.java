package com.example.appbackend.repository;

import com.example.appbackend.entity.SystemConfigTestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemConfigTestLogRepository extends JpaRepository<SystemConfigTestLog, Long> {

    Page<SystemConfigTestLog> findByConfigIdOrderByCreateTimeDescIdDesc(Long configId, Pageable pageable);

    Page<SystemConfigTestLog> findByConfigKeyStartingWithOrderByCreateTimeDescIdDesc(String configKeyPrefix, Pageable pageable);

    List<SystemConfigTestLog> findByConfigKeyStartingWithAndSuccessOrderByCreateTimeDescIdDesc(String configKeyPrefix, Boolean success, Pageable pageable);
}
