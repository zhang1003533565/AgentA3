package com.example.appbackend.repository;

import com.example.appbackend.entity.PaperDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaperDictionaryRepository extends JpaRepository<PaperDictionary, Long> {
    List<PaperDictionary> findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(String dictType);
    @Query("select d from PaperDictionary d where d.dictType = :type and d.enabled = true and (d.creatorId is null or d.creatorId = :userId) order by case when d.creatorId is null then 0 else 1 end, d.sortOrder asc, d.id asc")
    List<PaperDictionary> findVisibleByType(@Param("type") String type, @Param("userId") Long userId);
    Optional<PaperDictionary> findByDictTypeAndDictCode(String dictType, String dictCode);
    @Query("select d from PaperDictionary d where d.dictType = :type and d.name = :name and (d.creatorId is null or d.creatorId = :userId)")
    Optional<PaperDictionary> findVisibleByTypeAndName(@Param("type") String type, @Param("name") String name, @Param("userId") Long userId);
}
