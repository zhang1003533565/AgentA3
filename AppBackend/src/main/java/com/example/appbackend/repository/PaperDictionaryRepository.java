package com.example.appbackend.repository;

import com.example.appbackend.entity.PaperDictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperDictionaryRepository extends JpaRepository<PaperDictionary, Long> {
    List<PaperDictionary> findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(String dictType);
    Optional<PaperDictionary> findByDictTypeAndDictCode(String dictType, String dictCode);
    Optional<PaperDictionary> findByDictTypeAndName(String dictType, String name);
}
