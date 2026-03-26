package com.example.appbackend.repository;

import com.example.appbackend.entity.FavoriteDestination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteDestinationRepository extends JpaRepository<FavoriteDestination, Long> {

    List<FavoriteDestination> findByUserId(Long userId);

    Optional<FavoriteDestination> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndMarkerId(Long userId, Long markerId);

    void deleteByMarkerId(Long markerId);

    @Query(value = "SELECT marker_id, COUNT(*) as cnt FROM favorite_destination " +
           "GROUP BY marker_id ORDER BY cnt DESC LIMIT :limit",
           nativeQuery = true)
    Object[] findTopMarkers(@Param("limit") int limit);
}
