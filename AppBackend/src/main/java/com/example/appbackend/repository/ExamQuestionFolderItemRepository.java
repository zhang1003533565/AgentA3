package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamQuestionFolderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamQuestionFolderItemRepository extends JpaRepository<ExamQuestionFolderItem, Long> {

    long countByFolderId(Long folderId);

    boolean existsByFolderIdAndQuestionId(Long folderId, Long questionId);

    Optional<ExamQuestionFolderItem> findByFolderIdAndQuestionId(Long folderId, Long questionId);

    Page<ExamQuestionFolderItem> findByFolderIdOrderByCreateTimeDescIdDesc(Long folderId, Pageable pageable);

    @Query("""
            SELECT i.folderId AS folderId, COUNT(i.id) AS cnt
            FROM ExamQuestionFolderItem i
            WHERE i.folderId IN :folderIds
            GROUP BY i.folderId
            """)
    List<Object[]> countGroupedByFolderIds(@Param("folderIds") Collection<Long> folderIds);

    @Modifying
    @Query("DELETE FROM ExamQuestionFolderItem i WHERE i.folderId = :folderId")
    void deleteByFolderId(@Param("folderId") Long folderId);
}