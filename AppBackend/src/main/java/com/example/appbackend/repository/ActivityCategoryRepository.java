package com.example.appbackend.repository;

import com.example.appbackend.entity.ActivityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityCategoryRepository extends JpaRepository<ActivityCategory,Long> {
    boolean existsActivityCategoryByCategoryName(String categoryname);
}
