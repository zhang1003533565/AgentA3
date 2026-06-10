package com.example.appbackend.repository;

import com.example.appbackend.entity.DatasetProcessRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetProcessRuleRepository extends JpaRepository<DatasetProcessRule, Long> {

    List<DatasetProcessRule> findByDatasetIdOrderByCreateTimeDesc(Long datasetId);
}
