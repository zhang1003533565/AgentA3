package com.example.appbackend.repository;

import com.example.appbackend.entity.AgentModelBind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AgentModelBind entity
 * 只保留基础 CRUD 方法，不使用自定义命名查询
 */
@Repository
public interface AgentModelBindRepository extends JpaRepository<AgentModelBind, Long> {
}
