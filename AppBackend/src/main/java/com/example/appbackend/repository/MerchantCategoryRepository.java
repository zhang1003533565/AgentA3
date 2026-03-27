package com.example.appbackend.repository;

import com.example.appbackend.entity.MerchantCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

    List<MerchantCategory> findAllByStatusOrderBySortAsc(Integer status);

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.categoryId = :categoryId")
    long countByCategoryId(@Param("categoryId") Long categoryId);
}
