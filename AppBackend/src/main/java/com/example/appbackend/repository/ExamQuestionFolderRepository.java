package com.example.appbackend.repository;

import com.example.appbackend.entity.ExamQuestionFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamQuestionFolderRepository extends JpaRepository<ExamQuestionFolder, Long> {

    Optional<ExamQuestionFolder> findByIdAndStatus(Long id, Integer status);

    @Query("""
            SELECT f FROM ExamQuestionFolder f
            WHERE f.status = 1
              AND f.visibility = :visibility
              AND (
                    :visibility = 'PUBLIC'
                    OR (
                        :visibility = 'PRIVATE'
                        AND (
                              f.ownerUserId = :viewerId
                              OR (
                                    :admin = TRUE
                                    AND (:ownerUserId IS NULL OR f.ownerUserId = :ownerUserId)
                                    AND (:ownerIdsEmpty = TRUE OR f.ownerUserId IN :ownerIds)
                              )
                        )
                    )
              )
            ORDER BY f.updateTime DESC, f.id DESC
            """)
    List<ExamQuestionFolder> findVisibleFolders(
            @Param("visibility") String visibility,
            @Param("viewerId") Long viewerId,
            @Param("admin") boolean admin,
            @Param("ownerUserId") Long ownerUserId,
            @Param("ownerIds") Collection<Long> ownerIds,
            @Param("ownerIdsEmpty") boolean ownerIdsEmpty);
}
