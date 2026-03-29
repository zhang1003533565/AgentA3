package com.example.appbackend.repository;

import com.example.appbackend.entity.SecondhandFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecondhandFavoriteRepository extends JpaRepository<SecondhandFavorite, Long> {

    Optional<SecondhandFavorite> findByUserIdAndItemId(Long userId, Long itemId);

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    Page<SecondhandFavorite> findByUserId(Long userId, Pageable pageable);

    void deleteByItemId(Long itemId);
}
