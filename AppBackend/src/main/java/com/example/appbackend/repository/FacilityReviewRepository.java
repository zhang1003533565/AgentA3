package com.example.appbackend.repository;

import com.example.appbackend.entity.FacilityReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FacilityReviewRepository extends JpaRepository<FacilityReview, Long> {

    Page<FacilityReview> findByFacilityId(Long facilityId, Pageable pageable);

    @Query("SELECT AVG(r.score) FROM FacilityReview r WHERE r.facilityId = :facilityId")
    Double findAvgScoreByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT r.score, COUNT(r) FROM FacilityReview r WHERE r.facilityId = :facilityId GROUP BY r.score")
    Object[] findScoreDistributionByFacilityId(@Param("facilityId") Long facilityId);

    void deleteByFacilityId(Long facilityId);
}
