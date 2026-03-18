package com.example.appbackend.repository;

import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.Activity.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    @Query("SELECT a FROM Activity a WHERE " +
           "(:title IS NULL OR a.title LIKE %:title%) AND " +
           "(:categoryId IS NULL OR a.categoryId = :categoryId) AND " +
           "(:status IS NULL OR a.status = :status)")
    Page<Activity> findByConditions(
            @Param("title") String title,
            @Param("categoryId") Long categoryId,
            @Param("status") Status status,
            Pageable pageable);

    List<Activity> findByOrganizerId(Long organizerId);

    List<Activity> findByStatus(Status status);

    Page<Activity> findByStatus(Status status, Pageable pageable);

    boolean existsByCategoryId(Long id);

    @Query("SELECT a FROM Activity a WHERE " +
            "(:keyword IS NULL OR a.title LIKE %:keyword%)")
    Page<Activity> searchByKeyword(
            @Param("keyword") String keyword,
            Pageable pageable);
    /**
     * 按分类和状态筛选活动
     */
    @Query("SELECT a FROM Activity a WHERE " +
           "(:categoryId IS NULL OR a.categoryId = :categoryId) AND " +
           "(:status IS NULL OR a.status = :status)")
    Page<Activity> filterByCategoryAndStatus(
            @Param("categoryId") Long categoryId,
            @Param("status") Status status,
            Pageable pageable);


}
