package com.example.appbackend.repository;

import com.example.appbackend.entity.SecondhandCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecondhandCategoryRepository extends JpaRepository<SecondhandCategory, Long> {

    List<SecondhandCategory> findAllByOrderBySortAsc();

    @Query("SELECT COUNT(s) FROM SecondhandItem s WHERE s.categoryId = :categoryId")
    long countByCategoryId(@Param("categoryId") Long categoryId);
}
